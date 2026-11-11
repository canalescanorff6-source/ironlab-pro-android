"""Static preflight validation of the native Android IRONLAB project.

No Gradle/Android SDK is required for these checks.  It does NOT claim
that Kotlin compiles or media playback was verified on a device.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app' / 'src' / 'main'
XMLS = list(SRC.rglob('*.xml'))
for path in XMLS:
    ET.parse(path)
assert len(XMLS) >= 8, 'Expected manifest, layout and drawable resources'

manifest = (SRC / 'AndroidManifest.xml').read_text()
service = (SRC / 'java/com/ironlab/pro/PlaybackService.kt').read_text()
activity = (SRC / 'java/com/ironlab/pro/MainActivity.kt').read_text()
library = (SRC / 'java/com/ironlab/pro/MusicLibrary.kt').read_text()
gradle = (ROOT / 'app/build.gradle.kts').read_text()
workflow = (ROOT / '.github/workflows/build-debug-apk.yml').read_text()
layout = (SRC / 'res/layout/activity_main.xml').read_text()

# Android 14+ foreground-service type plus conditional local wake lock for lock-screen playback.
for token in ['android.permission.FOREGROUND_SERVICE',
              'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
              'android.permission.WAKE_LOCK',
              'android:foregroundServiceType="mediaPlayback"',
              'androidx.media3.session.MediaSessionService']:
    assert token in manifest, f'Missing manifest feature {token}'
assert 'setWakeMode(C.WAKE_MODE_LOCAL)' in service
assert 'setAudioAttributes(attrs, true)' in service
assert 'setHandleAudioBecomingNoisy(true)' in service
assert 'MediaSession.Builder(this, player).build()' in service

for forbidden in ['android.permission.MANAGE_EXTERNAL_STORAGE',
                  'android.permission.READ_MEDIA_AUDIO']:
    assert forbidden not in manifest, f'Unexpected broad permission {forbidden}'
assert 'android:usesCleartextTraffic="false"' in manifest

for token in ['takePersistableUriPermission', 'MediaController.Builder',
              'OpenMultipleDocuments', 'setMediaItems', 'favorites', 'playlists']:
    assert token in activity + library, f'Missing feature {token}'
assert 'https://ironlab-pro-3r9500.v2.appdeploy.ai/' in activity
assert 'browser.loadUrl(site + "#treinos")' in activity
assert 'SpotifyLinks(this)' in activity
assert 'spotifyLikedBtn' in layout and 'spotifySaveLinkBtn' in layout
assert 'spotifySavedLinks' in layout
assert 'android.intent.action.SEND' in manifest
spotify_links = (SRC / 'java/com/ironlab/pro/SpotifyLinks.kt').read_text()
assert 'https://open.spotify.com/collection/tracks' in spotify_links
assert 'android.intent.action.SEND' in manifest
assert 'https://open.spotify.com/' in spotify_links

for service_url in [
    'https://music.youtube.com/search',
    'https://open.spotify.com/search/',
    'https://www.deezer.com/search/',
    'https://music.apple.com/br/search?term='
]:
    assert service_url in activity, f'Missing external search: {service_url}'

# Every ViewBinding property used in MainActivity should correspond to a real XML ID.
xml_ids = set(re.findall(r'android:id="@\+id/([A-Za-z]\w*)"', layout))
binding_names = set(re.findall(r'\bbinding\.([A-Za-z]\w*)', activity)) - {'root', 'isInitialized'}
missing_ids = binding_names - xml_ids
assert not missing_ids, f'Missing IDs in activity_main.xml: {sorted(missing_ids)}'

assert 'media3-session:1.11.1' in gradle and 'media3-exoplayer:1.11.1' in gradle
assert 'namespace = "com.ironlab.pro"' in gradle
assert 'versionCode = 3' in gradle and 'versionName = "1.2.0"' in gradle
assert 'IRONLAB_KEYSTORE_FILE' in gradle and 'ironlabRelease' in gradle
assert 'gradle --no-daemon :app:assembleDebug' in workflow
assert 'gradle --no-daemon :app:assembleRelease' in workflow
assert 'actions/upload-artifact@v4' in workflow
assert 'python3 tests/validate_project.py' in workflow
assert 'IRONLAB_KEYSTORE_BASE64' in workflow

# The app must not try to defeat ads, bypass DRM or acquire proprietary audio covertly.
all_source = activity + library + service
for pattern in [r'client_secret', r'bypass_ads', r'youtube_dl', r'yt_dlp',
                r'android\.permission\.QUERY_ALL_PACKAGES']:
    assert not re.search(pattern, all_source, re.I), pattern

print(f'PASS: parsed {len(XMLS)} XML files and matched {len(binding_names)} view-binding IDs.')
print('PASS: Media3 service/foreground and screen-off wake-lock configuration.')
print('PASS: local media permissions, authorized search links and Gradle workflows.')
print('NOTE: Android SDK not installed; Kotlin compilation and screen-off playback require separate testing.')
