import { useEffect, useState } from 'react';

interface ChampionPortraitProps {
  name: string;
  portraitUrl: string;
  className?: string;
}

export function ChampionPortrait({ name, portraitUrl, className = '' }: ChampionPortraitProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => setFailed(false), [portraitUrl]);

  if (failed) {
    return (
      <span className={`champion-fallback ${className}`.trim()} aria-label={`${name} 이미지 대체`}>
        {name.replace(/\s/g, '').slice(0, 2)}
      </span>
    );
  }

  return (
    <img
      className={`champion-portrait ${className}`.trim()}
      src={portraitUrl}
      alt=""
      loading="eager"
      onError={() => setFailed(true)}
    />
  );
}
