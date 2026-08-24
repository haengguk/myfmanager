import { useMemo, useState, type KeyboardEvent, type PointerEvent } from 'react';
import type { GoldDifferencePointViewModel } from '../matchSession.types';

const CHART_WIDTH = 1000;
const CHART_HEIGHT = 180;
const VERTICAL_PADDING = 18;

const formatTime = (seconds: number) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
const formatGold = (gold: number) => `${(gold / 1000).toFixed(1)}K`;
const formatDifference = (difference: number) => difference === 0 ? '동률' : `${difference > 0 ? '+' : '−'}${(Math.abs(difference) / 1000).toFixed(1)}K`;

export function GoldDifferenceChart({ points, blueCode, redCode }: {
  points: readonly GoldDifferencePointViewModel[];
  blueCode: string;
  redCode: string;
}) {
  const [selectedIndex, setSelectedIndex] = useState(Math.max(0, points.length - 1));
  const chart = useMemo(() => {
    const duration = Math.max(1, points[points.length - 1]?.timeSeconds ?? 1);
    const maxDifference = Math.max(1000, ...points.map((point) => Math.abs(point.difference)));
    const x = (point: GoldDifferencePointViewModel) => point.timeSeconds / duration * CHART_WIDTH;
    const y = (point: GoldDifferencePointViewModel) => CHART_HEIGHT / 2
      - point.difference / maxDifference * (CHART_HEIGHT / 2 - VERTICAL_PADDING);
    const line = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${x(point).toFixed(2)} ${y(point).toFixed(2)}`).join(' ');
    return {
      duration,
      maxDifference,
      x,
      y,
      line,
      area: `${line} L ${CHART_WIDTH} ${CHART_HEIGHT / 2} L 0 ${CHART_HEIGHT / 2} Z`,
    };
  }, [points]);

  if (points.length === 0) {
    return <section className="rm-gold-difference"><h2>시간대별 골드 격차</h2><p>표시할 스냅샷이 없습니다.</p></section>;
  }

  const safeIndex = Math.min(selectedIndex, points.length - 1);
  const selected = points[safeIndex];
  const leadingCode = selected.difference > 0 ? blueCode : selected.difference < 0 ? redCode : '동률';
  const leadSummary = selected.difference === 0 ? '동률' : `${leadingCode} ${formatDifference(selected.difference)}`;
  const tone = selected.difference > 0 ? 'is-blue' : selected.difference < 0 ? 'is-red' : 'is-even';
  const selectNearest = (targetSeconds: number) => {
    let nearestIndex = 0;
    let nearestDistance = Number.POSITIVE_INFINITY;
    points.forEach((point, index) => {
      const distance = Math.abs(point.timeSeconds - targetSeconds);
      if (distance < nearestDistance) { nearestIndex = index; nearestDistance = distance; }
    });
    setSelectedIndex(nearestIndex);
  };
  const handlePointer = (event: PointerEvent<HTMLDivElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect();
    const ratio = Math.min(1, Math.max(0, (event.clientX - bounds.left) / bounds.width));
    selectNearest(ratio * chart.duration);
  };
  const handleKey = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    if (event.key === 'Home') setSelectedIndex(0);
    else if (event.key === 'End') setSelectedIndex(points.length - 1);
    else setSelectedIndex((current) => Math.min(points.length - 1, Math.max(0, current + (event.key === 'ArrowRight' ? 1 : -1))));
  };
  const ticks = Array.from({ length: 6 }, (_, index) => Math.round(chart.duration * index / 5));

  return (
    <section className="rm-gold-difference" aria-labelledby="rm-gold-difference-heading">
      <header>
        <h2 id="rm-gold-difference-heading">시간대별 골드 격차</h2>
        <span>실제 스냅샷 {points.length}개</span>
      </header>
      <div
        className="rm-gold-chart"
        role="img"
        tabIndex={0}
        aria-label={`${formatTime(selected.timeSeconds)} 시점 ${leadSummary}, ${blueCode} ${formatGold(selected.blueGold)}, ${redCode} ${formatGold(selected.redGold)}`}
        onPointerMove={handlePointer}
        onKeyDown={handleKey}
      >
        <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} preserveAspectRatio="none" aria-hidden="true" focusable="false">
          <defs>
            <linearGradient id="rm-gold-area" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="var(--rm-blue)" stopOpacity="0.24" />
              <stop offset="1" stopColor="var(--rm-blue)" stopOpacity="0.01" />
            </linearGradient>
          </defs>
          <line className="rm-gold-grid" x1="0" y1={CHART_HEIGHT * 0.25} x2={CHART_WIDTH} y2={CHART_HEIGHT * 0.25} />
          <line className="rm-gold-zero" x1="0" y1={CHART_HEIGHT / 2} x2={CHART_WIDTH} y2={CHART_HEIGHT / 2} />
          <line className="rm-gold-grid" x1="0" y1={CHART_HEIGHT * 0.75} x2={CHART_WIDTH} y2={CHART_HEIGHT * 0.75} />
          <path className="rm-gold-area" d={chart.area} />
          <path className="rm-gold-line" d={chart.line} />
          <line className="rm-gold-cursor" x1={chart.x(selected)} y1="0" x2={chart.x(selected)} y2={CHART_HEIGHT} />
          <circle className={`rm-gold-point ${tone}`} cx={chart.x(selected)} cy={chart.y(selected)} r="5" />
          <text className="rm-gold-limit" x="8" y="15">+{formatGold(chart.maxDifference)}</text>
          <text className="rm-gold-limit" x="8" y={CHART_HEIGHT - 7}>−{formatGold(chart.maxDifference)}</text>
        </svg>
        <div className={`rm-gold-readout ${tone}`} aria-hidden="true">
          <strong>{formatTime(selected.timeSeconds)}</strong>
          <span>{leadSummary}</span>
          <small>{blueCode} {formatGold(selected.blueGold)} · {redCode} {formatGold(selected.redGold)}</small>
        </div>
      </div>
      <div className="rm-gold-axis" aria-hidden="true">{ticks.map((tick) => <span key={tick}>{formatTime(tick)}</span>)}</div>
    </section>
  );
}
