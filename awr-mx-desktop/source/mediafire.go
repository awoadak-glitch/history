package main

import (
	"context"
	"errors"
	"fmt"
	"html"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"
)

// MediaFire's ordinary share URL returns HTML, not MP4 bytes. Resolve the
// short-lived download URL for playback, without ever downloading the video.
const mediafireBrowserUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

var mfDirectPattern = regexp.MustCompile(`(?i)https?://download[0-9]*\.mediafire\.com/[^\s"'<>\\]+`)
var mfHrefPattern = regexp.MustCompile(`(?i)href\s*=\s*["']([^"']+)["']`)

func isMediafireShare(raw string) bool {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || (u.Scheme != "https" && u.Scheme != "http") {
		return false
	}
	host := strings.ToLower(u.Hostname())
	return (host == "mediafire.com" || host == "www.mediafire.com") &&
		(strings.HasPrefix(u.EscapedPath(), "/file/") || strings.HasPrefix(u.EscapedPath(), "/download/"))
}

func mediafireDirect(raw string) bool {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || u.Scheme != "https" || u.User != nil {
		return false
	}
	host := strings.ToLower(u.Hostname())
	if !strings.HasPrefix(host, "download") || !strings.HasSuffix(host, ".mediafire.com") {
		return false
	}
	name := strings.TrimSuffix(strings.TrimPrefix(host, "download"), ".mediafire.com")
	if name == "" {
		return true
	}
	for _, ch := range name {
		if ch < '0' || ch > '9' {
			return false
		}
	}
	return true
}

func findMediafireURL(body []byte) (string, error) {
	// Support ordinary HTML download buttons and links embedded in JavaScript,
	// including common HTML entity and JSON slash escaping.
	page := html.UnescapeString(string(body))
	page = strings.ReplaceAll(page, `\/`, `/`)
	for _, pair := range [][2]string{{`\u0026`, "&"}, {`\u002F`, "/"}, {`\u002f`, "/"}, {`\u003A`, ":"}, {`\u003a`, ":"}} {
		page = strings.ReplaceAll(page, pair[0], pair[1])
	}
	candidates := mfDirectPattern.FindAllString(page, 80)
	// Match link href values in case the download host is not literally in the page body.
	for _, m := range mfHrefPattern.FindAllStringSubmatch(page, 50) {
		if len(m) > 1 {
			candidates = append(candidates, m[1])
		}
	}
	for _, item := range candidates {
		item = strings.TrimRight(item, `\"'),;`)
		item = html.UnescapeString(item)
		u, err := url.Parse(item)
		if err == nil && mediafireDirect(u.String()) {
			return u.String(), nil
		}
	}
	return "", errors.New("تعذر استخراج رابط الفيديو من صفحة MediaFire. قد يحتاج الموقع إلى تحقق بالمتصفح؛ افتح الصفحة وانسخ رابط زر Download المباشر أو نزّل الملف وشغّله محليًا")
}

func resolveMediafire(link string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 22*time.Second)
	defer cancel()
	client := &http.Client{
		Timeout: 20 * time.Second,
		CheckRedirect: func(req *http.Request, chain []*http.Request) error {
			if mediafireDirect(req.URL.String()) {
				return http.ErrUseLastResponse
			}
			if len(chain) >= 4 {
				return errors.New("too many redirects")
			}
			h := strings.ToLower(req.URL.Hostname())
			if h != "mediafire.com" && h != "www.mediafire.com" {
				return errors.New("unexpected MediaFire redirect")
			}
			return nil
		},
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, link, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("User-Agent", mediafireBrowserUA)
	req.Header.Set("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("تعذر الاتصال بصفحة MediaFire: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 && resp.StatusCode < 400 {
		loc, err := resp.Location()
		if err == nil && mediafireDirect(loc.String()) {
			return loc.String(), nil
		}
	}
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("MediaFire رفض فتح صفحة الملف (HTTP %d). افتحها من المتصفح للتأكد من عملها", resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 4*1024*1024))
	if err != nil {
		return "", fmt.Errorf("تعذر قراءة صفحة MediaFire: %w", err)
	}
	return findMediafireURL(body)
}
