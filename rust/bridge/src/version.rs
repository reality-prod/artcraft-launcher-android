//! Version ordering for release tags such as `v0.0.5-beta`, `0.12.1` or `pdfcraft-v0.12.1-android.3`.
use std::cmp::Ordering;

struct Parsed {
    nums: Vec<u64>,
    pre: Vec<String>,
}

fn parse(raw: &str) -> Parsed {
    // Drop any leading name and the "v": everything before the first digit.
    let s = raw.trim().trim_start_matches(|c: char| !c.is_ascii_digit());
    let s = s.split('+').next().unwrap_or("");
    let (core, pre) = match s.split_once('-') {
        Some((c, p)) => (c, p),
        None => (s, ""),
    };
    let nums = core
        .split('.')
        .map(|p| {
            p.chars()
                .take_while(|c| c.is_ascii_digit())
                .collect::<String>()
                .parse::<u64>()
                .unwrap_or(0)
        })
        .collect();
    let pre = if pre.is_empty() {
        Vec::new()
    } else {
        pre.split(['.', '-']).map(str::to_string).collect()
    };
    Parsed { nums, pre }
}

fn cmp_pre(a: &[String], b: &[String]) -> Ordering {
    // No pre-release outranks any pre-release (1.0.0 > 1.0.0-beta).
    match (a.is_empty(), b.is_empty()) {
        (true, true) => return Ordering::Equal,
        (true, false) => return Ordering::Greater,
        (false, true) => return Ordering::Less,
        _ => {}
    }
    for (x, y) in a.iter().zip(b.iter()) {
        let o = match (x.parse::<u64>(), y.parse::<u64>()) {
            (Ok(m), Ok(n)) => m.cmp(&n),
            (Ok(_), Err(_)) => Ordering::Less, // numeric identifiers sort before text
            (Err(_), Ok(_)) => Ordering::Greater,
            (Err(_), Err(_)) => x.cmp(y),
        };
        if o != Ordering::Equal {
            return o;
        }
    }
    a.len().cmp(&b.len())
}

/// -1, 0 or 1.
pub fn compare(a: &str, b: &str) -> i32 {
    let (pa, pb) = (parse(a), parse(b));
    let len = pa.nums.len().max(pb.nums.len());
    for i in 0..len {
        let x = pa.nums.get(i).copied().unwrap_or(0);
        let y = pb.nums.get(i).copied().unwrap_or(0);
        match x.cmp(&y) {
            Ordering::Equal => {}
            Ordering::Less => return -1,
            Ordering::Greater => return 1,
        }
    }
    match cmp_pre(&pa.pre, &pb.pre) {
        Ordering::Less => -1,
        Ordering::Equal => 0,
        Ordering::Greater => 1,
    }
}

#[cfg(test)]
mod tests {
    use super::compare;

    #[test]
    fn orders_plain_versions() {
        assert_eq!(compare("0.0.4", "0.0.5"), -1);
        assert_eq!(compare("v1.2.0", "1.2"), 0);
        assert_eq!(compare("0.12.1", "0.9.9"), 1);
    }

    #[test]
    fn prerelease_is_below_release() {
        assert_eq!(compare("0.0.5-beta", "0.0.5"), -1);
        assert_eq!(compare("0.0.5", "v0.0.5-beta"), 1);
        assert_eq!(compare("v0.0.5-beta", "0.0.5-beta"), 0);
        assert_eq!(compare("0.0.4-beta", "0.0.5-beta"), -1);
    }

    #[test]
    fn strips_names_and_android_suffixes() {
        assert_eq!(
            compare("pdfcraft-v0.12.1-android.2", "pdfcraft-v0.12.1-android.3"),
            -1
        );
        assert_eq!(compare("pdfcraft-v0.12.1-android.3", "0.12.0"), 1);
    }

    #[test]
    fn garbage_never_panics() {
        assert_eq!(compare("", ""), 0);
        assert_eq!(compare("latest", "also-latest"), 0);
        assert_eq!(compare("99999999999999999999999.1", "1"), -1); // overflow parses as 0
    }
}
