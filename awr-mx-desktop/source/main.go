package main

import (
	"bufio"
	"crypto/rand"
	"crypto/sha256"
	"embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

//go:embed web/*
var webFiles embed.FS

//go:embed gestures/awr_gestures.lua
var nativeGestures []byte

type Entry struct {
	ID         string    `json:"id"`
	Title      string    `json:"title"`
	Source     string    `json:"source"`
	Local      bool      `json:"local"`
	LastPlayed time.Time `json:"lastPlayed"`
	Position   float64   `json:"position"`
	Duration   float64   `json:"duration"`
	UserAgent  string    `json:"userAgent,omitempty"`
	Referer    string    `json:"referer,omitempty"`
}
type Request struct {
	Source    string   `json:"source"`
	Title     string   `json:"title"`
	HistoryID string   `json:"historyId"`
	UserAgent string   `json:"userAgent"`
	Referer   string   `json:"referer"`
	Cookie    string   `json:"cookie"`
	Subtitle  string   `json:"subtitle"`
	Start     *float64 `json:"start,omitempty"`
}
type Player struct {
	mu                      sync.Mutex
	playMu                  sync.Mutex
	history                 map[string]*Entry
	active                  string
	proc                    *exec.Cmd
	pipe                    string
	dataDir, appDir, engine string
	token                   string
	lastErr                 string
	baseURL                 string
	protocolReady           bool
	opening                 bool
}

func main() {
	exe, err := os.Executable()
	if err != nil {
		log.Fatal(err)
	}
	dir := filepath.Dir(exe)
	dataDir, err := os.UserConfigDir()
	if err != nil {
		dataDir = dir
	}
	dataDir = filepath.Join(dataDir, "AWR-MX-Desktop")
	_ = os.MkdirAll(dataDir, 0700)
	if len(os.Args) == 2 && os.Args[1] == "--register-protocol" {
		if err := registerProtocol(exe); err != nil {
			log.Fatal(err)
		}
		return
	}
	var incoming *Request
	var incomingError error
	link := ""
	if len(os.Args) > 1 {
		if len(os.Args) != 2 {
			incomingError = errors.New("رابط التشغيل غير صالح")
		} else if strings.HasPrefix(os.Args[1], "awrmx:") {
			q, e := parsePlaybackLink(os.Args[1])
			incomingError = e
			if e == nil {
				incoming = &q
				link = os.Args[1]
			}
		} else {
			incoming = &Request{Source: os.Args[1]}
		}
	}
	if incomingError == nil && (link != "" || len(os.Args) == 1) && forwardToInstance(dataDir, link) {
		return
	}
	p := &Player{history: map[string]*Entry{}, dataDir: dataDir, appDir: dir, token: randomToken()}
	p.load()
	p.engine = p.findEngine()
	p.protocolReady = registerProtocol(exe) == nil
	if incomingError != nil {
		p.lastErr = incomingError.Error()
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		serveEmbedded(w, r, "web/index.html", "text/html; charset=utf-8")
	})
	mux.HandleFunc("/style.css", func(w http.ResponseWriter, r *http.Request) {
		serveEmbedded(w, r, "web/style.css", "text/css; charset=utf-8")
	})
	mux.HandleFunc("/app.js", func(w http.ResponseWriter, r *http.Request) {
		serveEmbedded(w, r, "web/app.js", "application/javascript; charset=utf-8")
	})
	mux.HandleFunc("/api/", p.api)
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		log.Fatal(err)
	}
	server := &http.Server{Handler: securityHeaders(mux), ReadHeaderTimeout: 5 * time.Second}
	base := fmt.Sprintf("http://127.0.0.1:%d/?k=%s", l.Addr().(*net.TCPAddr).Port, p.token)
	p.baseURL = base
	_ = writeInstance(dataDir, Instance{Version: appVersion, Port: l.Addr().(*net.TCPAddr).Port, Token: p.token})
	go func() {
		if err := server.Serve(l); err != nil && err != http.ErrServerClosed {
			log.Println(err)
		}
	}()
	go p.background()
	if incoming != nil {
		go p.playHandoff(*incoming)
	}
	if !openDesktopWindow(base, p.dataDir) {
		fmt.Printf("Open AWR MX Desktop in your browser: %s\n", base)
	}
	// Keep the server alive when the user opens a browser instead of a standalone Edge window.
	select {}
}
func serveEmbedded(w http.ResponseWriter, r *http.Request, path, ctype string) {
	b, e := fs.ReadFile(webFiles, path)
	if e != nil {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", ctype)
	w.Write(b)
}
func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Referrer-Policy", "no-referrer")
		w.Header().Set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'")
		next.ServeHTTP(w, r)
	})
}
func randomToken() string        { b := make([]byte, 24); _, _ = rand.Read(b); return hex.EncodeToString(b) }
func (p *Player) dbPath() string { return filepath.Join(p.dataDir, "history.json") }
func (p *Player) load() {
	b, e := os.ReadFile(p.dbPath())
	if e == nil {
		_ = json.Unmarshal(b, &p.history)
	}
	if p.history == nil {
		p.history = map[string]*Entry{}
	}
}
func (p *Player) saveLocked() {
	b, e := json.MarshalIndent(p.history, "", "  ")
	if e == nil {
		tmp := p.dbPath() + ".tmp"
		if os.WriteFile(tmp, b, 0600) == nil {
			_ = os.Rename(tmp, p.dbPath())
		}
	}
}
func (p *Player) findEngine() string {
	paths := []string{filepath.Join(p.appDir, "player", "mpv.exe"), filepath.Join(p.appDir, "mpv.exe"), filepath.Join(p.dataDir, "player", "mpv.exe")}
	for _, x := range paths {
		if s, e := os.Stat(x); e == nil && !s.IsDir() {
			return x
		}
	}
	name := "mpv"
	if runtime.GOOS == "windows" {
		name = "mpv.exe"
	}
	if x, e := exec.LookPath(name); e == nil {
		return x
	}
	return ""
}
func idOf(s string) string {
	s = strings.TrimSpace(s)
	if u, err := url.Parse(s); err == nil && (u.Scheme == "http" || u.Scheme == "https") {
		u.RawQuery = ""
		u.Fragment = ""
		s = u.String()
	}
	h := sha256.Sum256([]byte(s))
	return hex.EncodeToString(h[:])
}
func guessTitle(s string) string {
	if u, e := url.Parse(s); e == nil && (u.Scheme == "http" || u.Scheme == "https") {
		s = u.Path
	}
	b := filepath.Base(strings.ReplaceAll(s, "\\", "/"))
	if b == "" || b == "." || b == "/" {
		return "فيديو جديد"
	}
	if u, e := url.PathUnescape(b); e == nil {
		b = u
	}
	return b
}
func (p *Player) api(w http.ResponseWriter, r *http.Request) {
	if r.Header.Get("X-AWR-Key") != p.token {
		http.Error(w, "unauthorized", http.StatusForbidden)
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	if r.Method == http.MethodPost && r.ContentLength > 64*1024 {
		http.Error(w, "request too large", 413)
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, 64*1024)
	switch r.URL.Path {
	case "/api/status":
		if r.Method != "GET" {
			http.Error(w, "method not allowed", 405)
			return
		}
		p.mu.Lock()
		p.engine = p.findEngine()
		active := p.active
		engine := p.engine
		errstr := p.lastErr
		opening := p.opening
		p.mu.Unlock()
		st := map[string]interface{}{"engine": engine != "", "enginePath": engine, "activeId": active, "error": errstr, "playing": false, "opening": opening, "protocol": p.protocolReady, "version": appVersion}
		if active != "" {
			if x, e := p.getMpvStats(); e == nil {
				for k, v := range x {
					st[k] = v
				}
				st["playing"] = true
				p.storeStats(x)
			}
		}
		send(w, st)
	case "/api/history":
		if r.Method != "GET" {
			http.Error(w, "method not allowed", 405)
			return
		}
		p.mu.Lock()
		xs := []Entry{}
		for _, x := range p.history {
			xs = append(xs, *x)
		}
		p.mu.Unlock()
		sort.Slice(xs, func(i, j int) bool { return xs[i].LastPlayed.After(xs[j].LastPlayed) })
		if len(xs) > 120 {
			xs = xs[:120]
		}
		send(w, xs)
	case "/api/handoff":
		if r.Method != "POST" {
			http.Error(w, "method not allowed", 405)
			return
		}
		var value struct {
			Link string `json:"link"`
		}
		if json.NewDecoder(r.Body).Decode(&value) != nil {
			fail(w, 400, "Invalid request")
			return
		}
		q, err := parsePlaybackLink(value.Link)
		if err != nil {
			fail(w, 400, err.Error())
			return
		}
		w.WriteHeader(http.StatusAccepted)
		send(w, map[string]bool{"accepted": true})
		go p.playHandoff(q)
	case "/api/focus":
		if r.Method != "POST" {
			http.Error(w, "method not allowed", 405)
			return
		}
		w.WriteHeader(http.StatusAccepted)
		send(w, map[string]bool{"accepted": true})
		go openDesktopWindow(p.baseURL, p.dataDir)
	case "/api/open":
		if r.Method != "POST" {
			http.Error(w, "method not allowed", 405)
			return
		}
		var req Request
		if e := json.NewDecoder(r.Body).Decode(&req); e != nil {
			fail(w, 400, "Invalid request")
			return
		}
		x, e := p.play(req)
		if e != nil {
			fail(w, 400, e.Error())
			return
		}
		send(w, x)
	case "/api/pick":
		if r.Method != "POST" {
			http.Error(w, "method not allowed", 405)
			return
		}
		name, e := pickFile("video")
		if e != nil {
			fail(w, 400, e.Error())
			return
		}
		send(w, map[string]string{"path": name})
	case "/api/subtitle":
		if r.Method != "POST" {
			http.Error(w, "method not allowed", 405)
			return
		}
		name, e := pickFile("subtitle")
		if e != nil {
			fail(w, 400, e.Error())
			return
		}
		_, e = p.ipc([]interface{}{"sub-add", name, "select"})
		if e != nil {
			fail(w, 400, e.Error())
			return
		}
		send(w, map[string]string{"path": name})
	case "/api/control":
		if r.Method != "POST" {
			http.Error(w, "method not allowed", 405)
			return
		}
		var v struct {
			Action string  `json:"action"`
			Value  float64 `json:"value"`
		}
		if e := json.NewDecoder(r.Body).Decode(&v); e != nil {
			fail(w, 400, "Invalid request")
			return
		}
		var cmd []interface{}
		switch v.Action {
		case "pause":
			cmd = []interface{}{"cycle", "pause"}
		case "seek":
			cmd = []interface{}{"seek", v.Value, "absolute", "exact"}
		case "jump":
			cmd = []interface{}{"seek", v.Value, "relative"}
		case "volume":
			if v.Value < 0 || v.Value > 100 {
				fail(w, 400, "مستوى صوت غير صالح")
				return
			}
			cmd = []interface{}{"set_property", "volume", v.Value}
		case "speed":
			if v.Value < 0.5 || v.Value > 4 {
				fail(w, 400, "السرعة من 0.5 إلى 4")
				return
			}
			cmd = []interface{}{"set_property", "speed", v.Value}
		case "fullscreen":
			cmd = []interface{}{"cycle", "fullscreen"}
		case "mute":
			cmd = []interface{}{"cycle", "mute"}
		case "stop":
			p.persistNow()
			cmd = []interface{}{"quit"}
		default:
			fail(w, 400, "Unknown control")
			return
		}
		_, e := p.ipc(cmd)
		if e != nil {
			fail(w, 400, e.Error())
			return
		}
		send(w, map[string]bool{"ok": true})
	case "/api/delete":
		if r.Method != "POST" {
			http.Error(w, "method not allowed", 405)
			return
		}
		var v struct {
			ID string `json:"id"`
		}
		if e := json.NewDecoder(r.Body).Decode(&v); e != nil {
			fail(w, 400, "Invalid request")
			return
		}
		p.mu.Lock()
		delete(p.history, v.ID)
		p.saveLocked()
		p.mu.Unlock()
		send(w, map[string]bool{"ok": true})
	case "/api/variants":
		if r.Method != "POST" {
			http.Error(w, "method not allowed", 405)
			return
		}
		var v struct{ Source, UserAgent, Referer, Cookie string }
		if e := json.NewDecoder(r.Body).Decode(&v); e != nil {
			fail(w, 400, "Invalid request")
			return
		}
		out, e := getVariants(v.Source, v.UserAgent, v.Referer, v.Cookie)
		if e != nil {
			fail(w, 400, e.Error())
			return
		}
		send(w, out)
	case "/api/quit":
		if r.Method != "POST" {
			http.Error(w, "method not allowed", 405)
			return
		}
		p.persistNow()
		_, _ = p.ipc([]interface{}{"quit"})
		send(w, map[string]bool{"ok": true})
		go func() { time.Sleep(120 * time.Millisecond); os.Exit(0) }()
	default:
		http.NotFound(w, r)
	}
}
func send(w http.ResponseWriter, v interface{}) { _ = json.NewEncoder(w).Encode(v) }
func fail(w http.ResponseWriter, c int, message string) {
	w.WriteHeader(c)
	send(w, map[string]string{"error": message})
}
func (p *Player) play(q Request) (Entry, error) {
	p.playMu.Lock()
	defer p.playMu.Unlock()
	q.Source = strings.TrimSpace(q.Source)
	if q.Source == "" {
		return Entry{}, errors.New("ضع رابط فيديو أو اختر ملفًا")
	}
	if strings.ContainsAny(q.Source, "\r\n\x00") {
		return Entry{}, errors.New("Invalid source")
	}
	local := false
	if !strings.HasPrefix(strings.ToLower(q.Source), "http://") && !strings.HasPrefix(strings.ToLower(q.Source), "https://") {
		if strings.Contains(q.Source, "://") {
			return Entry{}, errors.New("هذا الرابط يحتاج محللًا خاصًا؛ المشغل يدعم روابط HTTP/HTTPS النهائية")
		}
		s, e := os.Stat(q.Source)
		if e != nil || s.IsDir() {
			return Entry{}, errors.New("ملف غير موجود: " + q.Source)
		}
		local = true
	}
	p.mu.Lock()
	p.engine = p.findEngine()
	engine := p.engine
	p.mu.Unlock()
	if engine == "" {
		return Entry{}, errors.New("محرك mpv غير مثبت. شغّل ملف Setup-MPV.cmd الموجود مع البرنامج أولًا")
	}
	if len(q.UserAgent) > 500 || len(q.Referer) > 1024 || len(q.Cookie) > 3000 {
		return Entry{}, errors.New("Headers too long")
	}
	for _, x := range []string{q.UserAgent, q.Referer, q.Cookie} {
		if strings.ContainsAny(x, "\r\n\x00") {
			return Entry{}, errors.New("Invalid header")
		}
	}
	// MediaFire share links point to an HTML download page. Resolve a fresh,
	// short-lived mp4 URL only for playback; keep the original share URL in
	// history so it can be resolved again on subsequent launches.
	mediafirePage := isMediafireShare(q.Source)
	playbackSource := q.Source
	if mediafirePage {
		var resolveErr error
		playbackSource, resolveErr = resolveMediafire(q.Source)
		if resolveErr != nil {
			return Entry{}, resolveErr
		}
		if q.UserAgent == "" {
			q.UserAgent = mediafireBrowserUA
		}
		if q.Referer == "" {
			q.Referer = q.Source
		}
	}
	p.persistNow()
	p.mu.Lock()
	priorProc := p.proc
	priorPipe := p.pipe
	p.mu.Unlock()
	if priorProc != nil {
		var gracefulErr error
		if priorPipe != "" {
			_, gracefulErr = pipeCommand(priorPipe, []interface{}{"quit"})
		}
		if priorPipe == "" || gracefulErr != nil {
			_ = priorProc.Process.Kill()
		}
	}
	id := stableHistoryID(q)
	p.mu.Lock()
	if q.HistoryID != "" {
		if _, ok := p.history[q.HistoryID]; ok {
			id = q.HistoryID
		}
	}

	entry, exists := p.history[id]
	if !exists {
		entry = &Entry{ID: id, Source: q.Source, Local: local}
		p.history[id] = entry
	}
	// For expiring URLs, keep the latest signed query string, but use a stable ID.
	entry.Source = q.Source
	if strings.TrimSpace(q.Title) != "" {
		entry.Title = strings.TrimSpace(q.Title)
	} else if entry.Title == "" {
		entry.Title = guessTitle(q.Source)
	}
	entry.Local = local
	entry.UserAgent = q.UserAgent
	entry.Referer = q.Referer
	entry.LastPlayed = time.Now()
	start := entry.Position
	if q.Start != nil {
		start = *q.Start
		if start < 0 {
			start = 0
		}
		entry.Position = start
	}
	if entry.Duration > 0 && start > entry.Duration-12 {
		start = 0
	}
	p.active = id
	p.lastErr = ""
	p.saveLocked()
	result := *entry
	p.mu.Unlock()
	pipe := "\\\\.\\pipe\\awrmx-" + strconv.Itoa(os.Getpid()) + "-" + randomToken()[:10]
	if runtime.GOOS != "windows" {
		pipe = filepath.Join(p.dataDir, "mpv-"+randomToken()[:10]+".sock")
	}
	gestureFile, gestureErr := p.installNativeGestures()
	if gestureErr != nil {
		return Entry{}, fmt.Errorf("فشل تجهيز تحكم الضغط المطول: %w", gestureErr)
	}
	args := []string{"--no-config", "--terminal=no", "--force-window=yes", "--osc=yes", "--keep-open=no", "--hwdec=auto-safe", "--script-opts=osc-layout=bottombar,osc-seekbarstyle=knob,osc-hidetimeout=1350,osc-fadeduration=220,osc-boxalpha=55,osc-scalewindowed=1.06,osc-scalefullscreen=1.06,osc-background_color=#171B22,osc-timecode_color=#E7C16B,osc-buttons_color=#F3F1EC", "--script=" + gestureFile, "--input-ipc-server=" + pipe, "--title=AWR MX · " + result.Title, "--save-position-on-quit", "--watch-later-directory=" + filepath.Join(p.dataDir, "watch_later")}
	args = append(args, "--resume-playback=no")
	if start > 3 {
		args = append(args, fmt.Sprintf("--start=%.2f", start))
	}
	if q.UserAgent != "" {
		args = append(args, "--user-agent="+q.UserAgent)
	}
	if q.Referer != "" {
		args = append(args, "--referrer="+q.Referer)
	}
	if q.Cookie != "" {
		args = append(args, "--http-header-fields=Cookie: "+q.Cookie)
	}
	if q.Subtitle != "" {
		if _, e := os.Stat(q.Subtitle); e == nil {
			args = append(args, "--sub-file="+q.Subtitle)
		}
	}
	args = append(args, "--", playbackSource)
	cmd := exec.Command(engine, args...)
	hiddenCommand(cmd)
	if e := cmd.Start(); e != nil {
		p.mu.Lock()
		p.active = ""
		p.lastErr = e.Error()
		p.mu.Unlock()
		return Entry{}, fmt.Errorf("فشل فتح المشغل: %w", e)
	}
	p.mu.Lock()
	p.pipe = pipe
	p.proc = cmd
	p.mu.Unlock()
	go func() {
		err := cmd.Wait()
		p.mu.Lock()
		if p.proc == cmd {
			p.proc = nil
			p.pipe = ""
			p.active = ""
			if err != nil {
				p.lastErr = "تعذر تشغيل هذا الفيديو. جرّب سيرفرًا آخر أو تأكد من اتصال الإنترنت."
			}
		}
		p.mu.Unlock()
	}()
	return result, nil
}

// installNativeGestures copies the embedded gesture script into the app's private
// data directory so the Windows EXE is self-contained with respect to gestures.
// This does not install the mpv playback engine itself.
func (p *Player) installNativeGestures() (string, error) {
	path := filepath.Join(p.dataDir, "awr_mx_gestures.lua")
	if err := os.MkdirAll(p.dataDir, 0700); err != nil {
		return "", err
	}
	if len(nativeGestures) == 0 {
		return "", errors.New("embedded Lua script is empty")
	}
	if err := os.WriteFile(path, nativeGestures, 0600); err != nil {
		return "", err
	}
	return path, nil
}

func (p *Player) persistNow() {
	if st, e := p.getMpvStats(); e == nil {
		p.storeStats(st)
	}
}
func (p *Player) background() {
	for {
		time.Sleep(2 * time.Second)
		p.persistNow()
	}
}
func (p *Player) storeStats(st map[string]interface{}) {
	p.mu.Lock()
	defer p.mu.Unlock()
	currentID, _ := st["_activeId"].(string)
	if currentID == "" || currentID != p.active {
		return
	}
	entry := p.history[currentID]
	if entry == nil {
		return
	}
	timePos, ok := st["timePos"].(float64)
	if ok && timePos >= 1 {
		entry.Position = timePos
	}
	duration, ok := st["duration"].(float64)
	if ok && duration > 0 {
		entry.Duration = duration
	}
	p.saveLocked()
}
func (p *Player) getMpvStats() (map[string]interface{}, error) {
	p.mu.Lock()
	pipe := p.pipe
	active := p.active
	p.mu.Unlock()
	if pipe == "" || active == "" {
		return nil, errors.New("inactive")
	}
	out := map[string]interface{}{"_activeId": active}
	props := []struct{ key, prop string }{{"timePos", "time-pos"}, {"duration", "duration"}, {"paused", "pause"}, {"speed", "speed"}, {"volume", "volume"}}
	n := 0
	for _, prop := range props {
		x, e := pipeCommand(pipe, []interface{}{"get_property", prop.prop})
		if e == nil {
			out[prop.key] = x
			n++
		}
	}
	if n == 0 {
		return nil, errors.New("mpv not connected")
	}
	return out, nil
}
func (p *Player) ipc(cmd []interface{}) (interface{}, error) {
	p.mu.Lock()
	pipe := p.pipe
	p.mu.Unlock()
	if pipe == "" {
		return nil, errors.New("لا يوجد فيديو قيد التشغيل")
	}
	return pipeCommand(pipe, cmd)
}
func pipeCommand(pipe string, cmd []interface{}) (interface{}, error) {
	// On Windows, os.OpenFile opens a bidirectional mpv named pipe. On Unix, use a socket.
	var rw io.ReadWriteCloser
	var e error
	if runtime.GOOS == "windows" {
		for i := 0; i < 4; i++ {
			rw, e = os.OpenFile(pipe, os.O_RDWR, 0)
			if e == nil {
				break
			}
			time.Sleep(75 * time.Millisecond)
		}
	} else {
		rw, e = net.DialTimeout("unix", pipe, 400*time.Millisecond)
	}
	if e != nil {
		return nil, e
	}
	defer rw.Close()
	timer := time.AfterFunc(2*time.Second, func() { _ = rw.Close() })
	defer timer.Stop()
	b, _ := json.Marshal(map[string]interface{}{"command": cmd, "request_id": 1})
	if _, e = rw.Write(append(b, '\n')); e != nil {
		return nil, e
	}
	// mpv may first send async notifications; skip them until request_id=1.
	reader := bufio.NewReader(rw)
	type response struct {
		RequestID int         `json:"request_id"`
		Error     string      `json:"error"`
		Data      interface{} `json:"data"`
	}
	for i := 0; i < 15; i++ {
		line, e := reader.ReadBytes('\n')
		if e != nil {
			return nil, e
		}
		var v response
		if e := json.Unmarshal(line, &v); e == nil && v.RequestID == 1 {
			if v.Error != "success" {
				return nil, errors.New(v.Error)
			}
			return v.Data, nil
		}
	}
	return nil, errors.New("No mpv response")
}
func pickFile(kind string) (string, error) {
	if runtime.GOOS != "windows" {
		return "", errors.New("Native file picker is available on Windows")
	}
	filter := "Video files|*.mp4;*.mkv;*.avi;*.ts;*.m3u8;*.webm;*.mov;*.flv;*.wmv;*.mp3;*.m4a|All files|*.*"
	if kind == "subtitle" {
		filter = "Subtitles|*.srt;*.ass;*.ssa;*.vtt;*.sub|All files|*.*"
	}
	ps := `Add-Type -AssemblyName System.Windows.Forms; $d = New-Object System.Windows.Forms.OpenFileDialog; $d.Filter='` + filter + `'; if ($d.ShowDialog() -eq 'OK') { [Console]::OutputEncoding=[System.Text.Encoding]::UTF8; Write-Output $d.FileName }`
	cmd := exec.Command("powershell.exe", "-NoLogo", "-NoProfile", "-STA", "-Command", ps)
	hiddenCommand(cmd)
	b, e := cmd.Output()
	if e != nil {
		return "", e
	}
	path := strings.TrimSpace(strings.TrimPrefix(string(b), "\ufeff"))
	if path == "" {
		return "", errors.New("لم يتم اختيار ملف")
	}
	return path, nil
}
func openDesktopWindow(link string, dataDir string) bool {
	if runtime.GOOS != "windows" {
		_ = exec.Command("xdg-open", link).Start()
		return false
	}
	candidates := []string{
		filepath.Join(os.Getenv("ProgramFiles(x86)"), "Microsoft", "Edge", "Application", "msedge.exe"),
		filepath.Join(os.Getenv("ProgramFiles"), "Microsoft", "Edge", "Application", "msedge.exe"),
		filepath.Join(os.Getenv("LOCALAPPDATA"), "Microsoft", "Edge", "Application", "msedge.exe"),
	}
	if s, e := exec.LookPath("msedge.exe"); e == nil {
		candidates = append([]string{s}, candidates...)
	}
	for _, x := range candidates {
		if s, e := os.Stat(x); e == nil && !s.IsDir() {
			// --app uses a standalone chrome-less desktop window, not a regular browser tab.
			cmd := exec.Command(x, "--app="+link, "--no-first-run", "--window-size=1220,880", "--user-data-dir="+filepath.Join(dataDir, "edge-profile"))
			if e := cmd.Start(); e == nil {
				return true
			}
		}
	}
	_ = exec.Command("rundll32.exe", "url.dll,FileProtocolHandler", link).Start()
	return true
}
func getVariants(raw, agent, referer, cookie string) ([]map[string]string, error) {
	u, e := url.Parse(strings.TrimSpace(raw))
	if e != nil || (u.Scheme != "http" && u.Scheme != "https") {
		return nil, errors.New("ضع رابط HTTP/HTTPS لقائمة m3u8")
	}
	ctx := &http.Client{Timeout: 9 * time.Second}
	req, e := http.NewRequest("GET", u.String(), nil)
	if e != nil {
		return nil, e
	}
	if agent != "" {
		req.Header.Set("User-Agent", agent)
	}
	if referer != "" {
		req.Header.Set("Referer", referer)
	}
	if cookie != "" {
		req.Header.Set("Cookie", cookie)
	}
	res, e := ctx.Do(req)
	if e != nil {
		return nil, e
	}
	defer res.Body.Close()
	if res.StatusCode != 200 {
		return nil, fmt.Errorf("HTTP %d", res.StatusCode)
	}
	data, e := io.ReadAll(io.LimitReader(res.Body, 1024*1024))
	if e != nil {
		return nil, e
	}
	lines := strings.Split(strings.ReplaceAll(string(data), "\r", ""), "\n")
	out := []map[string]string{}
	pending := ""
	for _, rawline := range lines {
		line := strings.TrimSpace(rawline)
		if strings.HasPrefix(line, "#EXT-X-STREAM-INF:") {
			pending = line
			continue
		}
		if pending != "" && line != "" && !strings.HasPrefix(line, "#") {
			uri, e := url.Parse(line)
			if e == nil {
				base := res.Request.URL
				target := base.ResolveReference(uri).String()
				label := "جودة متاحة"
				attrs := strings.TrimPrefix(pending, "#EXT-X-STREAM-INF:")
				for _, a := range strings.Split(attrs, ",") {
					if strings.HasPrefix(strings.TrimSpace(a), "RESOLUTION=") {
						label = strings.TrimPrefix(strings.TrimSpace(a), "RESOLUTION=")
					}
				}
				if len(out) < 30 {
					out = append(out, map[string]string{"name": label, "url": target})
				}
			}
			pending = ""
		}
	}
	return out, nil
}
