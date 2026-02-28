VERSION := $(shell cat VERSION | tr -d '[:space:]')

MAJOR   := $(shell echo "$(VERSION)" | cut -d. -f1)
MINOR   := $(shell echo "$(VERSION)" | cut -d. -f2)
PATCH   := $(shell echo "$(VERSION)" | cut -d. -f3)

VERSION_CODE := $(shell echo "$$(( $(MAJOR) * 10000 + $(MINOR) * 100 + $(PATCH) ))")

GRADLE_FILE  := app/build.gradle.kts
TAG          := v$(VERSION)

.PHONY: help release tag update-version build-local install adb-grant adb-clear-proxy adb-check-proxy

# ── default ──────────────────────────────────────────────────────────────────
help:
	@echo ""
	@echo "  make release          Update gradle version, commit, tag and push → triggers CI"
	@echo "  make tag              Create and push git tag only (no gradle update)"
	@echo "  make update-version   Update versionCode/versionName in build.gradle.kts only"
	@echo "  make build-local      Build debug APK locally"
	@echo "  make install          Build and install debug APK on connected device"
	@echo "  make adb-grant        Grant WRITE_SECURE_SETTINGS to the app"
	@echo "  make adb-clear-proxy  Emergency: delete all system proxy settings on device"
	@echo "  make adb-check-proxy  Show all proxy-related settings on device (global + wifi)"
	@echo ""
	@echo "  Current: VERSION=$(VERSION)  versionCode=$(VERSION_CODE)  tag=$(TAG)"
	@echo ""

# ── main target ──────────────────────────────────────────────────────────────
release: _check-clean update-version _commit tag
	@echo ""
	@echo "✓ Released $(TAG)  (versionCode=$(VERSION_CODE))"

# ── update gradle ─────────────────────────────────────────────────────────────
update-version:
	@echo "→ Setting versionName = \"$(VERSION)\"  versionCode = $(VERSION_CODE)"
	@sed -i 's/versionCode = [0-9]*/versionCode = $(VERSION_CODE)/' $(GRADLE_FILE)
	@sed -i 's/versionName = "[^"]*"/versionName = "$(VERSION)"/' $(GRADLE_FILE)
	@echo "✓ $(GRADLE_FILE) updated"

# ── commit gradle change ──────────────────────────────────────────────────────
_commit:
	@git diff --quiet $(GRADLE_FILE) || ( \
		git add $(GRADLE_FILE) VERSION && \
		git commit -m "chore: bump version to $(VERSION) (versionCode=$(VERSION_CODE))" && \
		git push origin HEAD \
	)

# ── tag & push ────────────────────────────────────────────────────────────────
tag:
	@if git rev-parse "$(TAG)" >/dev/null 2>&1; then \
		echo "Tag $(TAG) already exists — skipping"; \
	else \
		git tag -a "$(TAG)" -m "Release $(TAG)"; \
		git push origin "$(TAG)"; \
		echo "✓ Tag $(TAG) pushed → GitHub Actions triggered"; \
	fi

# ── local build ───────────────────────────────────────────────────────────────
build-local:
	./gradlew assembleDebug

install:
	./gradlew installDebug

# ── ADB helpers ───────────────────────────────────────────────────────────────
adb-grant:
	adb shell pm grant com.hightemp.proxy_switcher android.permission.WRITE_SECURE_SETTINGS
	@echo "✓ WRITE_SECURE_SETTINGS granted"

adb-clear-proxy:
	-adb shell settings delete global http_proxy
	-adb shell settings delete global global_http_proxy_host
	-adb shell settings delete global global_http_proxy_port
	-adb shell settings delete global global_http_proxy_exclusion_list
	-adb shell settings delete global global_proxy_pac_url
	@echo "✓ All system proxy settings cleared (Settings.Global)"
	@echo "⚠  Per-network WiFi proxy is NOT affected. If Chrome still fails:"
	@echo "   WiFi Settings → long-press network → Modify → Proxy → None"

adb-check-proxy:
	@echo "=== Settings.Global proxy keys ==="
	-adb shell settings get global http_proxy
	-adb shell settings get global global_http_proxy_host
	-adb shell settings get global global_http_proxy_port
	-adb shell settings get global global_proxy_pac_url
	@echo "=== Per-network WiFi proxy (from dumpsys wifi) ==="
	-adb shell dumpsys wifi | grep -E "(HTTP proxy|Proxy settings)" | sort -u

# ── guard: warn if there are uncommitted changes ─────────────────────────────
_check-clean:
	@git diff --quiet || echo "⚠  Warning: uncommitted changes present"
