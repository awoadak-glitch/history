package main

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestIdStripsEphemeralToken(t *testing.T) {
	a := idOf("https://cdn.example/episode/video.m3u8?token=abc&expire=100")
	b := idOf("https://cdn.example/episode/video.m3u8?token=def&expire=200")
	if a != b {
		t.Fatalf("IDs should be same for refreshed token: %s %s", a, b)
	}
}
func TestSavedTimeSurvivesZeroAndSourceSwap(t *testing.T) {
	dir := t.TempDir()
	p := &Player{dataDir: dir, history: map[string]*Entry{"a": {ID: "a", Position: 81.2, Duration: 120}}, active: "a"}
	p.storeStats(map[string]interface{}{"_activeId": "a", "timePos": float64(0), "duration": float64(120)})
	if p.history["a"].Position != 81.2 {
		t.Fatal("Zero overwrote saved position")
	}
	p.storeStats(map[string]interface{}{"_activeId": "old", "timePos": float64(99)})
	if p.history["a"].Position != 81.2 {
		t.Fatal("Stats from wrong video were saved")
	}
	p.storeStats(map[string]interface{}{"_activeId": "a", "timePos": float64(94.4)})
	if p.history["a"].Position != 94.4 {
		t.Fatal("Progress was not updated")
	}
	b, err := os.ReadFile(filepath.Join(dir, "history.json"))
	if err != nil || !strings.Contains(string(b), "94.4") {
		t.Fatalf("progress missing from saved file: %s %v", b, err)
	}
	loaded := &Player{history: map[string]*Entry{}, dataDir: dir}
	loaded.load()
	if loaded.history["a"].Position != 94.4 {
		t.Fatal("Persistent resume failed")
	}
}
func TestGuessTitle(t *testing.T) {
	if guessTitle("https://example.com/media/episode%201.mp4?expires=1") != "episode 1.mp4" {
		t.Fatal("Incorrect decoded URL title")
	}
}

func TestHLSVariantDiscoveryAndHeaders(t *testing.T) {
	var gotHeader string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotHeader = r.Header.Get("Referer")
		_, _ = w.Write([]byte("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720\n720p/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1920x1080\n1080p/index.m3u8\n"))
	}))
	defer srv.Close()
	res, err := getVariants(srv.URL+"/master.m3u8", "test-agent", "https://app.example/", "")
	if err != nil {
		t.Fatal(err)
	}
	if len(res) != 2 || res[0]["name"] != "1280x720" || res[1]["name"] != "1920x1080" {
		t.Fatalf("incorrect variants: %#v", res)
	}
	if res[0]["url"] != srv.URL+"/720p/index.m3u8" {
		t.Fatal("relative URL not resolved")
	}
	if gotHeader != "https://app.example/" {
		t.Fatal("referer header missing")
	}
}

func TestNativeGesturesEmbeddedAndInstalled(t *testing.T) {
	if !strings.Contains(string(nativeGestures), "MBTN_LEFT") || !strings.Contains(string(nativeGestures), "HOLD_DELAY") {
		t.Fatal("Gesture script is not properly embedded")
	}
	p := &Player{dataDir: t.TempDir()}
	path, err := p.installNativeGestures()
	if err != nil {
		t.Fatal(err)
	}
	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(b) != string(nativeGestures) {
		t.Fatal("installed gesture script differs from embedded")
	}
}
