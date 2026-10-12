package main

import (
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strconv"
	"testing"
)

func testLink(value any) string {
	data, _ := json.Marshal(value)
	return "awrmx://play?data=" + base64.RawURLEncoding.EncodeToString(data)
}

func TestWebsiteHandoffPreservesUnicodeSignedURLAndStableEpisode(t *testing.T) {
	value := WebPlayback{Version: 1, Source: "https://cdn.example.com/ep.mp4?token=a%2Bb&expire=20", Title: "ناروتو · الحلقة 1", HistoryID: "palma:witcher:anime:Naruto:001", Referer: "https://example.com/", UserAgent: "PALMA"}
	request, err := parsePlaybackLink(testLink(value))
	if err != nil {
		t.Fatal(err)
	}
	if request.Source != value.Source || request.Title != value.Title || request.HistoryID != value.HistoryID || request.Referer != value.Referer {
		t.Fatalf("lost playback data: %#v", request)
	}
	id := stableHistoryID(request)
	request.Source = "https://other.example.com/new/file.mp4?signed=changed"
	if id != stableHistoryID(request) {
		t.Fatal("changing servers reset episode identity")
	}
	request.HistoryID = "palma:witcher:anime:Naruto:002"
	if id == stableHistoryID(request) {
		t.Fatal("different episodes share identity")
	}
}

func TestWebsiteCannotOpenLocalFilesOrInjectCommands(t *testing.T) {
	for _, source := range []string{"file:///C:/Windows/system.ini", "C:\\Windows\\system.ini", "--script=evil.lua", "https://user:password@example.com/video", "http://127.0.0.1/video", "http://192.168.1.1/video", "http://169.254.169.254/latest", "http://localhost/", "http://server.local/video", "http://[::1]/", "https://example.com/a\n--script=x"} {
		_, err := parsePlaybackLink(testLink(WebPlayback{Version: 1, Source: source, HistoryID: "palma:a:b:c:d"}))
		if err == nil {
			t.Fatalf("accepted %q", source)
		}
	}
	_, err := parsePlaybackLink(testLink(map[string]any{"v": 1, "source": "https://example.com/a.mp4", "historyId": "palma:a", "command": "powershell"}))
	if err == nil {
		t.Fatal("accepted unknown command")
	}
	_, err = parsePlaybackLink(testLink(WebPlayback{Version: 1, Source: "https://example.com/a.mp4", HistoryID: "palma:a", UserAgent: "x\r\nCookie: secret"}))
	if err == nil {
		t.Fatal("accepted header injection")
	}
}

func TestExistingInstanceReceivesAuthenticatedHandoff(t *testing.T) {
	dir := t.TempDir()
	key := "123456789012345678901234567890123456789012345678"
	got := ""
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-AWR-Key") != key || r.Method != "POST" || r.URL.Path != "/api/handoff" {
			t.Error("unauthenticated forwarding")
		}
		var value struct {
			Link string `json:"link"`
		}
		_ = json.NewDecoder(r.Body).Decode(&value)
		got = value.Link
		w.WriteHeader(http.StatusAccepted)
	}))
	defer srv.Close()
	u, _ := url.Parse(srv.URL)
	port, _ := strconv.Atoi(u.Port())
	if err := writeInstance(dir, Instance{Version: appVersion, Port: port, Token: key}); err != nil {
		t.Fatal(err)
	}
	link := testLink(WebPlayback{Version: 1, Source: "https://example.com/a.mp4", HistoryID: "palma:a"})
	if !forwardToInstance(dir, link) || got != link {
		t.Fatal("existing player did not receive the episode")
	}
}

func TestLocalAPIDoesNotExposeHistoryWithoutSession(t *testing.T) {
	p := &Player{token: "private-session"}
	req := httptest.NewRequest("GET", "/api/history", nil)
	r := httptest.NewRecorder()
	p.api(r, req)
	if r.Code != http.StatusForbidden {
		t.Fatal("history exposed")
	}
}
