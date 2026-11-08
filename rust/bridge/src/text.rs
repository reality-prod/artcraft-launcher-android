//! Text shown in the UI: file sizes, relative dates, and release notes as plain text.

pub fn human_size(bytes: u64) -> String {
    const UNITS: [&str; 5] = ["B", "KB", "MB", "GB", "TB"];
    if bytes < 1024 {
        return format!("{bytes} B");
    }
    let mut v = bytes as f64;
    let mut i = 0usize;
    while v >= 1024.0 && i + 1 < UNITS.len() {
        v /= 1024.0;
        i += 1;
    }
    let unit = UNITS.get(i).copied().unwrap_or("TB");
    format!("{v:.1} {unit}")
}

// ---------------------------------------------------------------- dates
fn field(s: &str, from: usize, len: usize) -> Option<i64> {
    s.get(from..from + len)?.parse::<i64>().ok()
}

/// Days since 1970-01-01 for a proleptic Gregorian date (Howard Hinnant's algorithm).
fn days_from_civil(y: i64, m: i64, d: i64) -> i64 {
    let y = if m <= 2 { y - 1 } else { y };
    let era = (if y >= 0 { y } else { y - 399 }) / 400;
    let yoe = y - era * 400;
    let mp = (m + 9) % 12;
    let doy = (153 * mp + 2) / 5 + d - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146_097 + doe - 719_468
}

/// Parses `2026-11-05T12:30:00Z` (what GitHub sends). Offsets and fractions are ignored.
pub fn parse_epoch(iso: &str) -> Option<i64> {
    let s = iso.trim();
    let (y, mo, d) = (field(s, 0, 4)?, field(s, 5, 2)?, field(s, 8, 2)?);
    let (h, mi, sec) = (field(s, 11, 2)?, field(s, 14, 2)?, field(s, 17, 2)?);
    if !(1..=12).contains(&mo) || !(1..=31).contains(&d) || h > 23 || mi > 59 || sec > 60 {
        return None;
    }
    Some(days_from_civil(y, mo, d) * 86_400 + h * 3_600 + mi * 60 + sec)
}

fn ago(n: i64, unit: &str) -> String {
    if n == 1 {
        format!("1 {unit} ago")
    } else {
        format!("{n} {unit}s ago")
    }
}

/// "3 days ago", "yesterday", "just now"; empty when the timestamp can't be read.
pub fn relative_time(iso: &str, now_epoch: i64) -> String {
    let Some(then) = parse_epoch(iso) else {
        return String::new();
    };
    let d = now_epoch.saturating_sub(then);
    match d {
        i64::MIN..=59 => "just now".to_string(),
        60..=3_599 => ago(d / 60, "minute"),
        3_600..=86_399 => ago(d / 3_600, "hour"),
        86_400..=172_799 => "yesterday".to_string(),
        172_800..=2_591_999 => ago(d / 86_400, "day"),
        2_592_000..=31_535_999 => ago(d / 2_592_000, "month"),
        _ => ago(d / 31_536_000, "year"),
    }
}

// ---------------------------------------------------------------- markdown
fn link_at(chars: &[char], open: usize) -> Option<(String, usize)> {
    if chars.get(open) != Some(&'[') {
        return None;
    }
    let close = (open + 1..chars.len()).find(|&j| chars.get(j) == Some(&']'))?;
    if chars.get(close + 1) != Some(&'(') {
        return None;
    }
    let end = (close + 2..chars.len()).find(|&k| chars.get(k) == Some(&')'))?;
    let text: String = chars.get(open + 1..close)?.iter().collect();
    Some((text, end + 1))
}

fn tag_end(chars: &[char], i: usize) -> Option<usize> {
    let next = chars.get(i + 1)?;
    if !(next.is_ascii_alphabetic() || *next == '/' || *next == '!') {
        return None;
    }
    (i + 1..chars.len().min(i + 200)).find(|&j| chars.get(j) == Some(&'>'))
}

fn strip_inline(s: &str) -> String {
    let chars: Vec<char> = s.chars().collect();
    let mut out = String::with_capacity(s.len());
    let mut i = 0usize;
    while let Some(&c) = chars.get(i) {
        if c == '!' && chars.get(i + 1) == Some(&'[') {
            if let Some((_alt, next)) = link_at(&chars, i + 1) {
                i = next; // images are dropped
                continue;
            }
        }
        if c == '[' {
            if let Some((text, next)) = link_at(&chars, i) {
                out.push_str(&text);
                i = next;
                continue;
            }
        }
        if c == '<' {
            if let Some(end) = tag_end(&chars, i) {
                i = end + 1;
                continue;
            }
        }
        if (c == '*' || c == '_' || c == '~') && chars.get(i + 1) == Some(&c) {
            i += 2;
            continue;
        }
        if c == '`' {
            i += 1;
            continue;
        }
        out.push(c);
        i += 1;
    }
    // Bare URLs (PR links, "Full Changelog") are noise on a phone.
    out.split_whitespace()
        .filter(|w| !(w.starts_with("https://") || w.starts_with("http://")))
        .collect::<Vec<_>>()
        .join(" ")
}

fn strip_heading(t: &str) -> &str {
    let hashes = t.chars().take_while(|c| *c == '#').count();
    if (1..=6).contains(&hashes) {
        if let Some(rest) = t.get(hashes..) {
            if rest.starts_with(' ') {
                return rest.trim_start();
            }
        }
    }
    t
}

fn is_rule(t: &str) -> bool {
    let marks = t.chars().filter(|c| !c.is_whitespace()).count();
    marks >= 3 && t.chars().all(|c| matches!(c, '-' | '*' | '_' | ' '))
}

fn list_item(t: &str) -> (&'static str, &str) {
    for m in ["- ", "* ", "+ "] {
        if let Some(rest) = t.strip_prefix(m) {
            return ("• ", rest);
        }
    }
    ("", t)
}

fn truncate(s: &str, max_chars: usize) -> String {
    if max_chars == 0 || s.chars().count() <= max_chars {
        return s.to_string();
    }
    let cut: String = s.chars().take(max_chars).collect();
    format!("{}…", cut.trim_end())
}

/// GitHub release markdown to readable plain text. `max_chars == 0` means no limit.
pub fn plain_notes(md: &str, max_chars: usize) -> String {
    let mut lines: Vec<String> = Vec::new();
    let mut in_fence = false;
    for raw in md.lines() {
        let t = raw.trim();
        if t.starts_with("```") || t.starts_with("~~~") {
            in_fence = !in_fence;
            continue;
        }
        if in_fence {
            lines.push(format!("    {t}"));
            continue;
        }
        if t.is_empty() {
            if lines.last().is_some_and(|l| !l.is_empty()) {
                lines.push(String::new());
            }
            continue;
        }
        if is_rule(t) {
            continue;
        }
        let (marker, body) = list_item(strip_heading(t));
        let text = strip_inline(body);
        if text.is_empty() {
            continue;
        }
        lines.push(format!("{marker}{text}"));
    }
    while lines.last().is_some_and(|l| l.is_empty()) {
        lines.pop();
    }
    truncate(&lines.join("\n"), max_chars)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sizes() {
        assert_eq!(human_size(0), "0 B");
        assert_eq!(human_size(1023), "1023 B");
        assert_eq!(human_size(1536), "1.5 KB");
        assert_eq!(human_size(80 * 1024 * 1024), "80.0 MB");
        assert!(human_size(u64::MAX).ends_with("TB"));
    }

    #[test]
    fn epoch_matches_known_dates() {
        assert_eq!(parse_epoch("1970-01-01T00:00:00Z"), Some(0));
        assert_eq!(parse_epoch("2000-03-01T00:00:00Z"), Some(11_017 * 86_400));
        assert_eq!(parse_epoch("2024-02-29T12:00:00Z"), Some(1_709_208_000));
        assert_eq!(parse_epoch("nonsense"), None);
        assert_eq!(parse_epoch("2026-13-01T00:00:00Z"), None);
    }

    #[test]
    fn relative_buckets() {
        let now = parse_epoch("2026-11-08T00:00:00Z").unwrap();
        assert_eq!(relative_time("2026-11-07T23:59:30Z", now), "just now");
        assert_eq!(relative_time("2026-11-07T23:30:00Z", now), "30 minutes ago");
        assert_eq!(relative_time("2026-11-07T23:00:00Z", now), "1 hour ago");
        assert_eq!(relative_time("2026-11-07T00:00:00Z", now), "yesterday");
        assert_eq!(relative_time("2026-11-05T00:00:00Z", now), "3 days ago");
        assert_eq!(relative_time("2026-08-01T00:00:00Z", now), "3 months ago");
        assert_eq!(relative_time("2024-11-01T00:00:00Z", now), "2 years ago");
        assert_eq!(relative_time("", now), "");
        assert_eq!(relative_time("2027-01-01T00:00:00Z", now), "just now");
    }

    #[test]
    fn notes_become_plain_text() {
        let md = "## What's new\n- Fix [crash](https://x.y) in **viewer**\n\n```\ncode\n```\n![img](a.png)\n";
        assert_eq!(
            plain_notes(md, 0),
            "What's new\n• Fix crash in viewer\n\n    code"
        );
    }

    #[test]
    fn notes_drop_urls_html_and_rules() {
        let md = "---\n* Added <b>zoom</b> by @sam in https://github.com/a/b/pull/12\n\nFull Changelog: https://github.com/a/b/compare/v1...v2\n#123 stays";
        assert_eq!(
            plain_notes(md, 0),
            "• Added zoom by @sam in\n\nFull Changelog:\n#123 stays"
        );
    }

    #[test]
    fn notes_truncate_on_char_boundaries() {
        assert_eq!(plain_notes("héllo wörld", 5), "héllo…");
        assert_eq!(plain_notes("", 10), "");
    }
}
