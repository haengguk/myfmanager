import { useState } from 'react';
import type { MatchIntegrityViewModel } from '../matchSession.types';

async function copyText(value: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(value);
  } catch {
    const area = document.createElement('textarea');
    area.value = value;
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.append(area);
    area.select();
    document.execCommand('copy');
    area.remove();
  }
}

function HashBlock({ label, value, expanded, onToggle, onCopy }: { label: string; value: string | null; expanded: boolean; onToggle: () => void; onCopy: () => void }) {
  return (
    <div className="rm-hash-block">
      <header><span>{label}</span><div><button type="button" disabled={!value} onClick={onToggle}>{expanded ? '접기' : '전체 보기'}</button><button type="button" disabled={!value} onClick={onCopy}>복사</button></div></header>
      <div className={`rm-tech rm-hash-value${expanded ? ' is-expanded' : ''}`}>{value ?? '기록 없음'}</div>
    </div>
  );
}

export function IntegrityAccordion({ integrity, onCopied }: { integrity: MatchIntegrityViewModel; onCopied: (label: string) => void }) {
  const [expanded, setExpanded] = useState({ output: false, replay: false });
  const copy = async (label: string, value: string | null) => {
    if (!value) return;
    await copyText(value);
    onCopied(label);
  };
  return (
    <details className="rm-integrity">
      <summary>재현 및 무결성 정보 <span>필요할 때만 확인</span></summary>
      <div className="rm-integrity-body">
        <div className="rm-integrity-meta">
          <span><small>seed</small><strong className="rm-tech">{integrity.seed}</strong></span>
          <span><small>응답 생성 시각</small><strong>{integrity.responseTime}</strong></span>
          <span><small>runtime profile</small><strong className="rm-tech">{integrity.runtimeProfile}</strong></span>
        </div>
        <HashBlock label="output hash" value={integrity.outputHash} expanded={expanded.output} onToggle={() => setExpanded((current) => ({ ...current, output: !current.output }))} onCopy={() => copy('output hash', integrity.outputHash)} />
        <HashBlock label="replay hash" value={integrity.replayHash} expanded={expanded.replay} onToggle={() => setExpanded((current) => ({ ...current, replay: !current.replay }))} onCopy={() => copy('replay hash', integrity.replayHash)} />
      </div>
    </details>
  );
}
