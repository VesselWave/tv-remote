import { useState } from 'react';
import { mdiGamepadRoundOutline, mdiGestureTap, mdiGithub, mdiTrayArrowDown } from '@mdi/js';
import { Button } from '@/components/ui/button';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { REPO_URL, RELEASES_URL, megabytes, releaseDate, type Release } from '@/release';
import { cn } from '@/lib/utils';

type Mode = 'buttons' | 'touchpad';

const screens: Record<Mode, { src: string; alt: string }> = {
  buttons: {
    src: '/screens/buttons.webp',
    alt: 'Wave Remote in Buttons mode: power, inputs and mute, volume and channel rockers, apps, keyboard, playback, and a blue OK button inside the D-pad.',
  },
  touchpad: {
    src: '/screens/touchpad.webp',
    alt: 'Wave Remote in Touchpad mode: the same top controls above a large touchpad with a scroll strip on its right edge.',
  },
};

function Icon({ path, className }: { path: string; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={className}>
      <path d={path} fill="currentColor" />
    </svg>
  );
}

export default function App({ release }: { release: Release | null }) {
  const [mode, setMode] = useState<Mode>('buttons');
  const facts = release ? `Version ${release.version} · ${megabytes(release.size)} · Android 7+` : 'Latest release · Android 7+';

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-[1180px] flex-col px-6 sm:px-10">
      <header className="flex h-20 shrink-0 items-center justify-between">
        <a href="/" className="-m-1 rounded-[14px] p-1 outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <img src="/icon.svg" alt="Wave Remote" width={40} height={40} className="size-10 rounded-[11px]" />
        </a>
        <Button asChild variant="ghost" className="-mr-3">
          <a href={REPO_URL}>
            <Icon path={mdiGithub} />
            GitHub
          </a>
        </Button>
      </header>

      <main className="grid flex-1 items-center gap-x-20 gap-y-14 pt-6 pb-16 lg:grid-cols-[minmax(0,1fr)_auto] lg:py-6">
        <div className="max-w-[34rem]">
          <h1 className="font-display text-[clamp(3.5rem,9vw,6rem)] leading-[0.9] font-[780] tracking-[-0.035em] [font-stretch:112%]">
            Wave Remote
          </h1>
          <p className="mt-6 max-w-[30ch] text-balance text-[clamp(1.125rem,2vw,1.3125rem)] leading-snug text-muted-foreground">
            {'Control your LG\u00a0webOS\u00a0TV from your Android phone, over your own Wi-Fi.'}
          </p>
          <div className="mt-10 flex flex-col items-start gap-4">
            <Button asChild size="lg">
              <a href="/download">
                <Icon path={mdiTrayArrowDown} />
                Download APK
              </a>
            </Button>
            <p className="text-[15px] text-muted-foreground tabular-nums">
              {facts}
            </p>
          </div>
        </div>

        <figure className="flex flex-col items-center gap-5 justify-self-center">
          <ToggleGroup
            type="single"
            value={mode}
            onValueChange={(value: string) => value && setMode(value as Mode)}
            aria-label="Remote mode"
            className="w-[min(100%,300px)]"
          >
            <ToggleGroupItem value="buttons">
              <Icon path={mdiGamepadRoundOutline} />
              Buttons
            </ToggleGroupItem>
            <ToggleGroupItem value="touchpad">
              <Icon path={mdiGestureTap} />
              Touchpad
            </ToggleGroupItem>
          </ToggleGroup>
          <div className="rounded-[44px] bg-card p-2.5 ring-1 ring-secondary">
            <div className="phone-screen relative aspect-[720/1447] overflow-hidden rounded-[34px] bg-background">
              {(Object.keys(screens) as Mode[]).map((key) => (
                <img
                  key={key}
                  src={screens[key].src}
                  alt={screens[key].alt}
                  aria-hidden={key !== mode}
                  width={720}
                  height={1447}
                  fetchPriority={key === 'buttons' ? 'high' : 'low'}
                  className={cn(
                    'absolute inset-0 size-full object-cover transition-[opacity,filter] duration-300 ease-out motion-reduce:transition-none',
                    key === mode ? 'opacity-100 blur-0' : 'opacity-0 blur-[6px]',
                  )}
                />
              ))}
            </div>
          </div>
        </figure>
      </main>

      <footer className="flex shrink-0 flex-wrap items-center gap-x-6 gap-y-2 border-t border-secondary py-6 text-[13px] text-muted-foreground">
        {release ? (
          <a href={release.page} className="hover:text-foreground">
            Released {releaseDate(release.published)}
          </a>
        ) : (
          <a href={RELEASES_URL} className="hover:text-foreground">Release notes</a>
        )}
        {release?.sha256 ? (
          <span className="font-mono tabular-nums" title={release.sha256}>
            SHA-256 {release.sha256.slice(0, 8)}…{release.sha256.slice(-8)}
          </span>
        ) : null}
        <span className="sm:ml-auto">Independent project, not affiliated with LG.</span>
      </footer>
    </div>
  );
}
