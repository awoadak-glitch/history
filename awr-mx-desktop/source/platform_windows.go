package main

import (
	"fmt"
	"os/exec"
	"syscall"
)

func hiddenCommand(cmd *exec.Cmd) { cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true} }

// Current-user registration: no administrator privileges and no file associations
// are changed. Moving the portable folder is supported by opening the EXE again.
func registerProtocol(exe string) error {
	root := `HKCU\Software\Classes\awrmx`
	values := [][]string{
		{"add", root, "/ve", "/t", "REG_SZ", "/d", "URL:AWR MX Playback", "/f"},
		{"add", root, "/v", "URL Protocol", "/t", "REG_SZ", "/d", "", "/f"},
		{"add", root + `\DefaultIcon`, "/ve", "/t", "REG_SZ", "/d", exe + ",0", "/f"},
		{"add", root + `\shell\open\command`, "/ve", "/t", "REG_SZ", "/d", `"` + exe + `" "%1"`, "/f"},
	}
	for _, args := range values {
		cmd := exec.Command("reg.exe", args...)
		hiddenCommand(cmd)
		if err := cmd.Run(); err != nil {
			return fmt.Errorf("تعذر ربط المشغّل بالموقع: %w", err)
		}
	}
	return nil
}
