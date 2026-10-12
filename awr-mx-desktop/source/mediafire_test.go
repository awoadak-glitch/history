package main

import (
	"strings"
	"testing"
)

func TestMediafireShareRecognition(t *testing.T) {
	examples := []struct {
		s        string
		expected bool
	}{
		{"https://www.mediafire.com/file/9pvsgor84dzku2i/Tondemo_Skill_de_Isekai_Hourou_Meshi_002_1080p.mp4/file", true},
		{"https://mediafire.com/download/abc/file.mp4", true},
		{"https://download2285.mediafire.com/hash/file.mp4", false},
		{"https://mediafire.com.evil.example/file/abc/file", false},
		{"https://www.mediafire.com/", false},
	}
	for _, e := range examples {
		if got := isMediafireShare(e.s); got != e.expected {
			t.Fatalf("%s: %v wanted %v", e.s, got, e.expected)
		}
	}
}
func TestExtractMediafireDirectButton(t *testing.T) {
	u := "https://download2285.mediafire.com/abc-DEF_x/9pvsgor84dzku2i/Tondemo%2BSkill%2Bde%2BIsekai%2BHourou%2BMeshi%2B002%2B1080p.mp4"
	htmlPage := `<html><a class="dl-btn-label" id="downloadButton" href="` + u + `">Download (357.41MB)</a></html>`
	got, err := findMediafireURL([]byte(htmlPage))
	if err != nil || got != u {
		t.Fatalf("%s: %q", err, got)
	}
}
func TestMediafireExtractEscapedAndRejectHostSpoof(t *testing.T) {
	u := "https://download2285.mediafire.com/token/video.mp4?key=one&part=two"
	escaped := `<script>var a="https:\/\/download2285.mediafire.com\/token\/video.mp4?key=one&amp;part=two";</script>`
	got, err := findMediafireURL([]byte(escaped))
	if err != nil || got != u {
		t.Fatalf("escaped %q %v", got, err)
	}
	_, err = findMediafireURL([]byte(`<a href="https://download2285.mediafire.com.evil.org/a.mp4">download</a>`))
	if err == nil {
		t.Fatal("should reject external link")
	}
	if !strings.Contains(err.Error(), "MediaFire") {
		t.Fatal(err)
	}
}
