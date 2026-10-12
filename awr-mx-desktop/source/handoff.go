package main

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const appVersion = "7.0.0"
const palmaWebsite = "https://palma-web-lac.vercel.app/"

// The website sends only media data. A link can never select an executable,
// arbitrary mpv options, a local file, a cookie, or an application action.
type WebPlayback struct {
	Version   int    `json:"v"`
	Source    string `json:"source"`
	Title     string `json:"title"`
	HistoryID string `json:"historyId"`
	Referer   string `json:"referer,omitempty"`
	UserAgent string `json:"userAgent,omitempty"`
}

func parsePlaybackLink(raw string) (Request, error) {
	bad := errors.New("رابط المشاهدة غير صالح. جهّز رابطًا جديدًا من موقع بالما")
	if len(raw) > 24000 || strings.ContainsAny(raw, "\r\n\x00\"\\ ") {
		return Request{}, bad
	}
	u, err := url.Parse(raw)
	if err != nil || u.Scheme != "awrmx" || u.Host != "play" || (u.Path != "" && u.Path != "/") || u.User != nil || u.Fragment != "" {
		return Request{}, bad
	}
	query, err := url.ParseQuery(u.RawQuery)
	if err != nil || len(query) != 1 || len(query["data"]) != 1 {
		return Request{}, bad
	}
	data, err := base64.RawURLEncoding.DecodeString(query.Get("data"))
	if err != nil || len(data) > 16000 {
		return Request{}, bad
	}
	var value WebPlayback
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if decoder.Decode(&value) != nil || decoder.Decode(new(any)) != io.EOF || value.Version != 1 {
		return Request{}, bad
	}
	if !publicMediaAddress(value.Source) || len(value.Source) > 10000 || len(value.Title) > 1000 || len(value.HistoryID) > 1000 || len(value.UserAgent) > 500 || len(value.Referer) > 1024 {
		return Request{}, bad
	}
	for _, s := range []string{value.Source, value.Title, value.HistoryID, value.UserAgent, value.Referer} {
		if strings.ContainsAny(s, "\r\n\x00") {
			return Request{}, bad
		}
	}
	if value.Referer != "" && !publicMediaAddress(value.Referer) {
		return Request{}, bad
	}
	if value.HistoryID == "" || !strings.HasPrefix(value.HistoryID, "palma:") {
		return Request{}, bad
	}
	return Request{Source: value.Source, Title: value.Title, HistoryID: value.HistoryID, Referer: value.Referer, UserAgent: value.UserAgent}, nil
}

func publicMediaAddress(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.User != nil || u.Opaque != "" {
		return false
	}
	host := strings.ToLower(u.Hostname())
	if host == "" || !strings.Contains(host, ".") || (u.Port() != "" && u.Port() != "443" && u.Port() != "80") {
		return false
	}
	for _, suffix := range []string{"localhost", "local", "internal", "localdomain", "home.arpa"} {
		if host == suffix || strings.HasSuffix(host, "."+suffix) {
			return false
		}
	}
	if ip := net.ParseIP(host); ip != nil {
		return ip.IsGlobalUnicast() && !ip.IsPrivate() && !ip.IsLoopback() && !ip.IsLinkLocalUnicast() && !strings.HasPrefix(host, "169.254.")
	}
	return !strings.ContainsAny(host, " \\%")
}

type Instance struct {
	Version string `json:"version"`
	Port    int    `json:"port"`
	Token   string `json:"token"`
}

func instancePath(dir string) string { return filepath.Join(dir, "instance.json") }

func writeInstance(dir string, instance Instance) error {
	data, err := json.Marshal(instance)
	if err != nil {
		return err
	}
	return os.WriteFile(instancePath(dir), data, 0600)
}

// Only the native process reads this per-user token. The public website never
// gets access to the local API, history, file picker, or control endpoints.
func forwardToInstance(dir, link string) bool {
	data, err := os.ReadFile(instancePath(dir))
	if err != nil {
		return false
	}
	var instance Instance
	if json.Unmarshal(data, &instance) != nil || instance.Version != appVersion || instance.Port < 1024 || instance.Port > 65535 || len(instance.Token) != 48 {
		return false
	}
	endpoint := "/api/focus"
	body := []byte("{}")
	if link != "" {
		endpoint = "/api/handoff"
		body, _ = json.Marshal(map[string]string{"link": link})
	}
	req, err := http.NewRequest(http.MethodPost, fmt.Sprintf("http://127.0.0.1:%d%s", instance.Port, endpoint), bytes.NewReader(body))
	if err != nil {
		return false
	}
	req.Header.Set("X-AWR-Key", instance.Token)
	req.Header.Set("Content-Type", "application/json")
	client := &http.Client{Timeout: 2 * time.Second, Transport: &http.Transport{Proxy: nil}}
	response, err := client.Do(req)
	if err != nil {
		return false
	}
	defer response.Body.Close()
	return response.StatusCode == http.StatusAccepted
}

func (p *Player) playHandoff(q Request) {
	p.mu.Lock()
	p.opening = true
	p.lastErr = ""
	p.mu.Unlock()
	_, err := p.play(q)
	p.mu.Lock()
	p.opening = false
	if err != nil {
		p.lastErr = err.Error()
	}
	p.mu.Unlock()
}

func stableHistoryID(q Request) string {
	if strings.HasPrefix(q.HistoryID, "palma:") && len(q.HistoryID) <= 1000 {
		return "palma-" + idOf(q.HistoryID)
	}
	return idOf(q.Source)
}
