"""Generate crawlable locale pages with no client-side translation dependency.

Edit site-template.html and docs/locales/*.json, then run this script.
Use --catalog once after intentional English copy changes to refresh en.json.
"""
from html.parser import HTMLParser
from html import escape
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
BASE = 'https://arpanjot-coding.github.io/AVault/'
GUIDE_PATH = 'guides/android-call-recording/'
LANGUAGES = {
    'en': ('English', 'en_US'), 'ru': ('Русский', 'ru_RU'),
    'zh': ('简体中文', 'zh_CN'), 'ja': ('日本語', 'ja_JP'),
    'vi': ('Tiếng Việt', 'vi_VN'), 'th': ('ไทย', 'th_TH'),
    'hi': ('हिन्दी', 'hi_IN'), 'ar': ('العربية', 'ar_AR'),
    'fr': ('Français', 'fr_FR'), 'de': ('Deutsch', 'de_DE'),
    'es': ('Español', 'es_ES'), 'it': ('Italiano', 'it_IT'),
}
META = {'description', 'og:title', 'og:description', 'og:image:alt',
        'twitter:title', 'twitter:description'}
TEXT_ATTRIBUTES = {'alt', 'aria-label'}
TEMPLATE = (ROOT / 'tools/site-template.html').read_text(encoding='utf-8')
CATALOG_PATH = DOCS / 'locales/en.json'

class Catalog(HTMLParser):
    def __init__(self, previous_strings):
        super().__init__(convert_charrefs=False)
        self.values = {}
        self.previous_ids = {value: key for key, value in previous_strings.items()}
        self.next_id = max((int(key[1:]) for key in previous_strings), default=0) + 1
        self.skip = False
        self.schema = False

    def add(self, value):
        value = value.strip()
        if value and any(c.isalpha() for c in value) and value not in {'AVault', 'GitHub'} and value not in self.values:
            identifier = self.previous_ids.get(value)
            if identifier is None:
                identifier = f's{self.next_id:03}'
                self.next_id += 1
            self.values[value] = identifier

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in {'script', 'style'}:
            self.skip = True
            self.schema = attrs.get('type') == 'application/ld+json'
        if tag == 'meta' and (attrs.get('name') or attrs.get('property')) in META:
            self.add(attrs.get('content', ''))
        for key in TEXT_ATTRIBUTES:
            if key in attrs:
                self.add(attrs[key])

    def handle_endtag(self, tag):
        if tag in {'script', 'style'}:
            self.skip = self.schema = False

    def handle_data(self, data):
        if self.schema:
            schema = json.loads(data)
            for item in schema['@graph']:
                if 'description' in item:
                    self.add(item['description'])
        elif not self.skip:
            self.add(data)

previous_strings = json.loads(CATALOG_PATH.read_text(encoding='utf-8')) if CATALOG_PATH.exists() else {}
catalog = Catalog(previous_strings)
catalog.feed(TEMPLATE)
ENGLISH = dict(sorted((identifier, value) for value, identifier in catalog.values.items()))
(DOCS / 'locales').mkdir(exist_ok=True)
if '--catalog' in sys.argv:
    CATALOG_PATH.write_text(json.dumps(ENGLISH, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(f'English catalog: {len(ENGLISH)} strings')
    sys.exit(0)

def url(code):
    return BASE + (f'{code}/' if code != 'en' else '')

def switcher(current):
    links = ''.join(f'<li><a href="{("../" if current != "en" else "") + (code+"/" if code != "en" else "./")}" lang="{code}" hreflang="{code}"'
                    + (' aria-current="page"' if code == current else '') + f'>{name}</a></li>'
                    for code, (name, _) in LANGUAGES.items())
    return '<details class="language-picker"><summary><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c5 5 5 13 0 18-5-5-5-13 0-18"/></svg>' + LANGUAGES[current][0] + '<span aria-hidden="true">⌄</span></summary><ul>' + links + '</ul></details>'

class Render(HTMLParser):
    def __init__(self, code, strings):
        super().__init__(convert_charrefs=False)
        self.code = code
        self.strings = strings
        self.output = []
        self.skip = False
        self.schema = False

    def translated(self, value):
        identifier = catalog.values.get(value.strip())
        return self.strings[identifier] if identifier else value

    def handle_decl(self, decl):
        self.output.append('<!'+decl+'>')

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'html':
            attrs.update(lang=self.code, dir='rtl' if self.code == 'ar' else 'ltr')
        if tag in {'script', 'style'}:
            self.skip = True
            self.schema = attrs.get('type') == 'application/ld+json'
        if tag == 'meta':
            kind = attrs.get('name') or attrs.get('property')
            if kind in META:
                attrs['content'] = self.translated(attrs['content'])
            elif kind == 'og:url':
                attrs['content'] = url(self.code)
            elif kind == 'og:locale':
                attrs['content'] = LANGUAGES[self.code][1]
        for key in TEXT_ATTRIBUTES:
            if key in attrs:
                attrs[key] = self.translated(attrs[key])
        for key in ['src', 'href']:
            value = attrs.get(key, '')
            if self.code != 'en' and value and not value.startswith(('http', '#', '/', 'data:')):
                attrs[key] = '../' + value
        if tag == 'link' and attrs.get('rel') == 'canonical':
            attrs['href'] = url(self.code)
        rendered = ''.join(' '+key+(f'="{escape(value, quote=True)}"' if value is not None else '') for key, value in attrs.items())
        self.output.append('<'+tag+rendered+'>')

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        self.output[-1] = self.output[-1][:-1] + ' />'

    def handle_endtag(self, tag):
        if tag in {'script', 'style'}:
            self.skip = self.schema = False
        if tag == 'head':
            for code in LANGUAGES:
                hreflang = 'zh-Hans' if code == 'zh' else code
                self.output.append(f'<link rel="alternate" hreflang="{hreflang}" href="{url(code)}">\n')
            self.output.append(f'<link rel="alternate" hreflang="x-default" href="{BASE}">\n')
        self.output.append('</'+tag+'>')

    def handle_data(self, data):
        if self.schema:
            schema = json.loads(data)
            for item in schema['@graph']:
                if item['@type'] == 'WebPage':
                    item.update({
                        '@id': url(self.code) + '#page',
                        'url': url(self.code),
                        'inLanguage': 'zh-Hans' if self.code == 'zh' else self.code,
                    })
                if 'description' in item:
                    item['description'] = self.translated(item['description'])
            self.output.append(json.dumps(schema, ensure_ascii=False, indent=2))
        elif self.skip or not data.strip():
            self.output.append(data)
        else:
            match = re.match(r'(\s*)(.*?)(\s*)$', data, re.DOTALL)
            self.output.append(match[1] + escape(self.translated(match[2]), quote=False) + match[3])

    def handle_entityref(self, name):
        self.output.append('&'+name+';')

    def handle_charref(self, name):
        self.output.append('&#'+name+';')

    def handle_comment(self, data):
        if data.strip() == 'LANGUAGE_SWITCHER':
            self.output.append(switcher(self.code))
        else:
            self.output.append('<!--'+data+'-->')

for code in LANGUAGES:
    if '--english-only' in sys.argv and code != 'en':
        continue
    strings = ENGLISH if code == 'en' else json.loads((DOCS / 'locales' / f'{code}.json').read_text(encoding='utf-8'))
    missing = set(ENGLISH) - strings.keys()
    extra = strings.keys() - set(ENGLISH)
    if missing or extra:
        raise ValueError(f'{code}: missing={missing}, extra={extra}')
    if any(not isinstance(value, str) or not value.strip() for value in strings.values()):
        raise ValueError(f'{code}: empty or invalid translation')
    parser = Render(code, strings)
    parser.feed(TEMPLATE)
    destination = DOCS if code == 'en' else DOCS / code
    destination.mkdir(exist_ok=True)
    (destination / 'index.html').write_text(''.join(parser.output), encoding='utf-8')

alternates = ''.join(f'<xhtml:link rel="alternate" hreflang="{("zh-Hans" if code == "zh" else code)}" href="{url(code)}"/>' for code in LANGUAGES)
alternates += f'<xhtml:link rel="alternate" hreflang="x-default" href="{BASE}"/>'
entries = ''.join(f'<url><loc>{url(code)}</loc>{alternates}</url>' for code in LANGUAGES)
entries += f'<url><loc>{BASE}{GUIDE_PATH}</loc></url>'
(DOCS / 'sitemap.xml').write_text('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">'+entries+'</urlset>\n', encoding='utf-8')
print(f'Generated {len(LANGUAGES)} locale pages with {len(ENGLISH)} strings each, canonical URLs, hreflang and sitemap.')
