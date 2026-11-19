"""Package the published fitness shell for the Android offline fallback."""
from hashlib import sha256
from html.parser import HTMLParser
import json
from pathlib import Path
import re
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen

SITE = 'https://ironlab-pro-3r9500.v2.appdeploy.ai/'
TARGET = Path(__file__).resolve().parents[1] / 'app/src/main/assets/offline'


def download(url):
    request = Request(url, headers={'User-Agent': 'IRONLAB-Android-Build/1.6.0'})
    with urlopen(request, timeout=45) as response:
        if response.status != 200:
            raise RuntimeError(f'Unexpected status for {url}')
        data = response.read(8_000_001)
        if len(data) > 8_000_000:
            raise RuntimeError('Resource exceeds the packaging limit')
        return data


class Resources(HTMLParser):
    def __init__(self):
        super().__init__()
        self.urls = set()

    def handle_starttag(self, tag, attributes):
        attributes = dict(attributes)
        source = attributes.get('src') if tag == 'script' else attributes.get('href')
        if not source or source.startswith('data:'):
            return
        url = urljoin(SITE, source)
        parsed = urlsplit(url)
        if parsed.scheme != 'https':
            return
        if parsed.netloc == urlsplit(SITE).netloc and parsed.path.startswith('/assets/'):
            self.urls.add(url)
        if tag == 'script' and url == 'https://cdn.jsdelivr.net/npm/axios@1.21.1/dist/axios.min.js':
            self.urls.add(url)


def main():
    html = download(SITE).decode('utf-8')
    match = re.search(r'"appVersion":"([0-9]+)"', html)
    if not match:
        raise RuntimeError('Could not identify the published fitness version')
    site_version = match.group(1)
    resources = Resources()
    resources.feed(html)
    if not any(url.endswith('.js') and '/assets/' in url for url in resources.urls):
        raise RuntimeError('Missing application bundle')
    mapping = {}
    texts = []
    pending = list(resources.urls)
    while pending:
        url = pending.pop()
        if url in mapping:
            continue
        data = download(url)
        suffix = Path(urlsplit(url).path).suffix
        name = 'resources/' + sha256(url.encode()).hexdigest() + suffix
        path = TARGET / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        mapping[url] = name
        if suffix not in {'.js', '.css'}:
            continue
        text = data.decode('utf-8')
        texts.append(text)
        # Vite modules and preload lists use quoted hashed asset paths.
        for asset in re.findall(r'''["']((?:\./|/)?assets/[^"'\s]+\.(?:js|css))["']''', text):
            dependency = urljoin(SITE, asset)
            if dependency not in mapping:
                pending.append(dependency)
    bundle = '\n'.join(texts)
    for feature in ['upper-priority-tue-fri-v1', 'workoutJournal', 'suggestedLoad']:
        if feature not in bundle:
            raise RuntimeError(f'Fitness bundle is missing {feature}')
    (TARGET / 'index.html').write_text(html, encoding='utf-8')
    (TARGET / 'manifest.json').write_text(json.dumps({
        'version': site_version,
        'resources': mapping,
        'sha256': {name: sha256((TARGET / name).read_bytes()).hexdigest() for name in mapping.values()}
    }, indent=2), encoding='utf-8')
    print(f'Packaged fitness version {site_version}: {len(mapping)} resources')


if __name__ == '__main__':
    main()

