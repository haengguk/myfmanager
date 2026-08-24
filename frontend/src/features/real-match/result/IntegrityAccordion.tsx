import { useState } from 'react';
import type { MatchIntegrityViewModel } from '../matchSession.types';

async function copyText(value: string): Promise<void> {
  try { await navigator.clipboard.writeText(value); }
  catch {
    const area = document.createElement('textarea'); area.value = value; area.style.position = 'fixed'; area.style.opacity = '0';
    document.body.append(area); area.select(); document.execCommand('copy'); area.remove();
  }
}

function HashBlock({ id, label, value, expanded, onToggle, onCopy }: { id: string; label: string; value: string; expanded: boolean; onToggle: () => void; onCopy: () => void }) {
  return <div className="rm-hash-block"><header><span>{label}</span><div><button type="button" aria-expanded={expanded} aria-controls={id} onClick={onToggle}>{expanded ? '접기' : '전체 보기'}</button><button type="button" onClick={onCopy}>복사</button></div></header><div id={id} className={`rm-tech rm-hash-value${expanded ? ' is-expanded' : ''}`}>{value}</div></div>;
}

export function IntegrityAccordion({ integrity, onCopied }: { integrity: MatchIntegrityViewModel; onCopied: (label: string) => void }) {
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  const hashes = [
    ['output', 'output hash', integrity.outputHash],
    ['replay', 'replay provenance', integrity.replayHash],
    ['structured', 'structured timeline', integrity.structuredTimelineHash],
    ['simulator', 'simulator timeline', integrity.simulatorTimelineHash],
    ['resource', 'resource provenance', integrity.resourceProvenanceHash],
    ['configuration', 'configuration hash', integrity.configurationHash],
    ['policy', 'policy hash', integrity.policyHash],
    ['random', `Random trace · ${integrity.randomDrawCount.toLocaleString('ko-KR')} draws`, integrity.randomTraceHash],
    ...(integrity.manifestRawSha256 ? [['manifest', 'source manifest raw SHA', integrity.manifestRawSha256] as const] : []),
  ] as const;
  const toggle = (id: string) => setExpanded((current) => { const next = new Set(current); if (next.has(id)) next.delete(id); else next.add(id); return next; });
  const copy = async (label: string, value: string) => { await copyText(value); onCopied(label); };
  return (
    <details className="rm-integrity">
      <summary>재현 및 무결성 정보 <span>{integrity.source} · {integrity.sourceLabel}</span></summary>
      <div className="rm-integrity-body">
        <div className="rm-integrity-meta">
          <span><small>seed</small><strong className="rm-tech">{integrity.seed}</strong></span>
          <span><small>runtime profile</small><strong className="rm-tech">{integrity.runtimeProfile}</strong></span>
          <span><small>engine</small><strong className="rm-tech">{integrity.engineImplementationVersion}</strong></span>
        </div>
        {hashes.map(([id, label, value]) => <HashBlock key={id} id={`rm-hash-${id}`} label={label} value={value} expanded={expanded.has(id)} onToggle={() => toggle(id)} onCopy={() => copy(label, value)} />)}
      </div>
    </details>
  );
}
