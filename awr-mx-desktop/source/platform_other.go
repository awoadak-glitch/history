//go:build !windows

package main

import (
	"errors"
	"os/exec"
)

func hiddenCommand(cmd *exec.Cmd) {}
func registerProtocol(exe string) error {
	return errors.New("ربط الموقع متاح في نسخة Windows")
}
