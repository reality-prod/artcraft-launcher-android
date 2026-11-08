# Getting the launcher noticed by the artcraft-android maintainers

I don't have a private contact for them, and I haven't found an email. GitHub is the one channel
that is verifiably theirs, so use it:

1. Open an issue (or a Discussion, if enabled) on one of their repos, for example
   `artcraft-android/pdfcraft-android`. Link it from the other repos if you like.
2. For the desktop launcher side, `storytold/craft-launcher` has an open proposal about listing
   unofficial Craft-style apps as data. It's the natural place to mention an Android launcher.

The launcher works without their involvement: it only reads their public releases. The things
that would make it better are small asks, so keep the message short and friendly:

> **Title:** Unofficial Android launcher that installs your releases
>
> Hi! I'm publishing an open-source launcher, github.com/reality-prod/artcraft-launcher-android,
> that lists the artcraft-android apps and installs the latest release for the phone's CPU. It only
> reads your public GitHub releases and verifies downloads before installing. It isn't affiliated with you
> and doesn't redistribute your APKs.
>
> Four small things would make it more reliable, if you're happy to:
> 1. Publish a `SHA256SUMS.txt` with each release (GitHub's per-asset digest works too).
> 2. Keep asset names stable: one plain arm64 APK and one `-armeabi-v7a` APK.
> 3. Sign every release with the same key.
> 4. Tell me if you'd rather it didn't list an app, or want a different description or icon.
>
> Thanks for building these!
