import { useEffect, useId, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import type { ChampionProficiencyDto } from '../api/teamPlayerApi.types';
import type { LinkViewModel, PlayerProfileViewModel } from '../teamPlayer.adapter';

import { PlayerPortrait, PlayerPhotoCredit } from './PlayerPortrait';

const TABS = [
  { id: 'ratings', label: '능력치' },
  { id: 'proficiency', label: '챔피언 숙련도' },
  { id: 'career', label: '커리어·수상' },
  { id: 'contract', label: '계약·출처' },
] as const;
type TabId = typeof TABS[number]['id'];

function OptionalText({ value }: { value: string | null }) {
  return <>{value ?? '기록 없음'}</>;
}

function SourceLink({ source }: { source: LinkViewModel }) {
  return source.href
    ? <a href={source.href} target="_blank" rel="noreferrer">출처 열기<span className="lm-sr-only"> (새 창)</span></a>
    : <span>{source.label}</span>;
}

function ChampionPortrait({ champion }: { champion: ChampionProficiencyDto }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [champion.portraitUrl]);
  if (failed) return <span className="tp-champion__fallback" aria-label={`${champion.displayNameKo} 챔피언 이미지 대체`}>{champion.displayNameKo.slice(0, 2)}</span>;
  return <img src={champion.portraitUrl} alt={`${champion.displayNameKo} 챔피언 초상`} loading="lazy" onError={() => setFailed(true)} />;
}

function RatingsPanel({ profile }: { profile: PlayerProfileViewModel }) {
  return (
    <section className="tp-tab-section" aria-labelledby="tp-ratings-title">
      <header className="tp-section-heading">
        <div><p>AUTHORED ATTRIBUTES</p><h3 id="tp-ratings-title">{profile.position} 선수 능력치</h3></div>
        <span>{profile.ratingScale.min}–{profile.ratingScale.max} · API 순서</span>
      </header>
      <p className="tp-context-note">공통 6개와 포지션별 6개로 구성된 원본 정수 값입니다. 평균·OVR·등급은 계산하지 않습니다.</p>
      <ol className="tp-rating-list" aria-label={`${profile.nickname} 능력치 12개`}>
        {profile.ratings.map((rating, index) => (
          <li key={rating.key}>
            <span className="tp-rating__order">{String(index + 1).padStart(2, '0')}</span>
            <span className="tp-rating__label">{rating.displayNameKo}<small>{rating.skill}</small></span>
            <span className="tp-rating__track" aria-hidden="true"><span style={{ '--tp-rating-width': `${rating.value / profile.ratingScale.max * 100}%` } as CSSProperties} /></span>
            <strong aria-label={`${rating.displayNameKo} ${rating.value}점`}>{rating.value}</strong>
          </li>
        ))}
      </ol>
      <p className="tp-resource-id">RESOURCE · {profile.ratingScale.resourceVersion}</p>
    </section>
  );
}

function ProficiencyPanel({ profile }: { profile: PlayerProfileViewModel }) {
  return (
    <section className="tp-tab-section" aria-labelledby="tp-proficiency-title">
      <header className="tp-section-heading">
        <div><p>SPARSE PROFICIENCY</p><h3 id="tp-proficiency-title">저작된 챔피언 숙련도</h3></div>
        <span>{profile.proficiency.authoredEntryCount}개 · {profile.proficiency.min}–{profile.proficiency.max}</span>
      </header>
      <div className="tp-semantic-callout">
        <strong>목록은 authored override만 표시합니다.</strong>
        <span>목록에 없는 합법적인 {profile.position} 챔피언 키는 0이나 미보유가 아니라 neutral fallback <b>{profile.proficiency.neutralFallback}</b>로 해석됩니다.</span>
      </div>
      {profile.proficiencies.length === 0 ? (
        <p className="tp-empty-state" role="status">이 선수에게 저작된 sparse override가 없습니다. 합법 키에는 neutral fallback {profile.proficiency.neutralFallback}가 적용됩니다.</p>
      ) : (
        <ol className="tp-champion-list" aria-label={`${profile.nickname} 저작 숙련도 ${profile.proficiencies.length}개`}>
          {profile.proficiencies.map((champion) => (
            <li key={champion.championId}>
              <ChampionPortrait champion={champion} />
              <span className="tp-champion__name"><strong>{champion.displayNameKo}</strong><small>{champion.displayNameEn}</small></span>
              <code>{champion.championId}</code>
              <span className="tp-position-tag">{champion.position}</span>
              <strong className="tp-champion__value">{champion.value}</strong>
            </li>
          ))}
        </ol>
      )}
      <p className="tp-resource-id">RESOURCE · {profile.proficiency.resourceVersion}</p>
    </section>
  );
}

function CareerPanel({ profile }: { profile: PlayerProfileViewModel }) {
  return (
    <div className="tp-tab-stack">
      <section className="tp-tab-section" aria-labelledby="tp-career-title">
        <header className="tp-section-heading">
          <div><p>CAREER TIMELINE</p><h3 id="tp-career-title">팀 이력</h3></div>
          <span>데뷔 {profile.debutDate} · 활동 {profile.yearsActiveAtSnapshot}년</span>
        </header>
        <p className="tp-context-note">원본 순서와 날짜 정밀도를 보존합니다. 종료일이 없는 행만 “현재”로 표시합니다.</p>
        <ol className="tp-history-list">
          {profile.teamHistory.map((entry, index) => (
            <li key={`${entry.team}-${entry.from}-${index}`}>
              <span className="tp-history__rail" aria-hidden="true" />
              <div><strong>{entry.team}</strong><span>{entry.role}</span></div>
              <div><span>{entry.from} — {entry.toLabel}</span><small>정밀도 · {entry.datePrecision}</small></div>
            </li>
          ))}
        </ol>
        <p className="tp-coverage">Coverage · {profile.careerCoverage}</p>
      </section>
      <section className="tp-tab-section tp-honors" aria-labelledby="tp-honors-title">
        <header className="tp-section-heading"><div><p>MAJOR HONORS</p><h3 id="tp-honors-title">주요 수상 경력</h3></div></header>
        <p className="tp-context-note">모든 마이너 대회나 주간상을 망라하는 목록이 아닙니다. 팀 성과와 개인상을 구분합니다.</p>
        <div className="tp-honors-grid">
          <div><h4>팀 성과 <span>{profile.teamAchievements.length}</span></h4>
            {profile.teamAchievements.length === 0 ? <p className="tp-empty-state">기록된 주요 팀 성과가 없습니다.</p> : (
              <ul>{profile.teamAchievements.map((item, index) => <li key={`${item.season}-${item.competition}-${index}`}><strong>{item.result}</strong><span>{item.season} · {item.competition}</span><small>{item.team} · <SourceLink source={item.source} /></small></li>)}</ul>
            )}
          </div>
          <div><h4>개인상 <span>{profile.individualAwards.length}</span></h4>
            {profile.individualAwards.length === 0 ? <p className="tp-empty-state">기록된 주요 개인상이 없습니다.</p> : (
              <ul>{profile.individualAwards.map((item, index) => <li key={`${item.season}-${item.award}-${index}`}><strong>{item.award}</strong><span>{item.season} · {item.competition}</span><small><SourceLink source={item.source} /></small></li>)}</ul>
            )}
          </div>
        </div>
        <p className="tp-coverage">Coverage · {profile.honorsCoverage}</p>
      </section>
    </div>
  );
}

function ContractPanel({ profile }: { profile: PlayerProfileViewModel }) {
  const prize = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(profile.prizeMoneyAmountUsd);
  return (
    <div className="tp-tab-stack">
      <section className="tp-tab-section" aria-labelledby="tp-contract-title">
        <header className="tp-section-heading"><div><p>SNAPSHOT CONTRACT</p><h3 id="tp-contract-title">계약 정보</h3></div><span>{profile.contractStatus}</span></header>
        <dl className="tp-fact-grid">
          <div><dt>종료일</dt><dd>{profile.contractEndDate}</dd></div>
          <div><dt>스냅샷 잔여일</dt><dd>{profile.contractDaysRemaining}일</dd></div>
          <div><dt>출처 유형</dt><dd>{profile.contractSourceType}</dd></div>
          <div><dt>출처 스냅샷</dt><dd><OptionalText value={profile.contractSourceSnapshotAt} /></dd></div>
          <div><dt>확인일</dt><dd><OptionalText value={profile.contractCheckedAt} /></dd></div>
        </dl>
      </section>
      <section className="tp-tab-section" aria-labelledby="tp-prize-title">
        <header className="tp-section-heading"><div><p>PUBLIC TOURNAMENT WINNINGS</p><h3 id="tp-prize-title">커리어 상금</h3></div><span>{profile.prizeMoneyStatus}</span></header>
        <p className="tp-prize"><strong>{profile.prizeMoneyCurrency} {prize}</strong><span>공개 대회 상금의 근사치이며 급여·보너스·바이아웃·시장 가치가 아닙니다.</span></p>
        <dl className="tp-fact-grid"><div><dt>출처 유형</dt><dd>{profile.prizeMoneySourceType}</dd></div><div><dt>확인일</dt><dd><OptionalText value={profile.prizeMoneyCheckedAt} /></dd></div></dl>
      </section>
      <details className="tp-details">
        <summary>출처 인용과 데이터 품질 <span>{profile.sources.length}개 출처</span></summary>
        <div className="tp-quality"><strong>DATA QUALITY</strong>{Object.entries(profile.dataQuality).map(([key, value]) => <span key={key}><b>{key}</b>{value}</span>)}</div>
        {profile.sources.length === 0 ? <p className="tp-empty-state">표시할 출처 인용이 없습니다.</p> : (
          <ol className="tp-source-list">{profile.sources.map((source, index) => (
            <li key={`${source.type}-${index}`}><div><strong>{source.type}</strong><span><OptionalText value={source.sourceSnapshotAt} /></span></div><code><OptionalText value={source.path} /></code><div><SourceLink source={source.link} /><small>확인 · <OptionalText value={source.checkedAt} /></small></div></li>
          ))}</ol>
        )}
      </details>
    </div>
  );
}

interface PlayerProfileProps { profile: PlayerProfileViewModel }

export function PlayerProfile({ profile }: PlayerProfileProps) {
  const [activeTab, setActiveTab] = useState<TabId>('ratings');
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const instanceId = useId();

  useEffect(() => setActiveTab('ratings'), [profile.playerId]);

  const handleTabKey = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    let next = index;
    if (event.key === 'ArrowRight') next = (index + 1) % TABS.length;
    else if (event.key === 'ArrowLeft') next = (index - 1 + TABS.length) % TABS.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = TABS.length - 1;
    else return;
    event.preventDefault();
    setActiveTab(TABS[next].id);
    tabRefs.current[next]?.focus();
  };

  return (
    <article className="tp-profile" aria-labelledby={`${instanceId}-player-name`}>
      <header className="tp-profile__toolbar">
        <div><p>{profile.currentTeamCode} · {profile.position}</p><h2 id={`${instanceId}-player-name`}>{profile.nickname}</h2><code>{profile.playerId}</code></div>
        <span className="tp-contract-badge"><i aria-hidden="true" />계약 {profile.contractStatus} · {profile.contractEndDate}</span>
      </header>
      <div className="tp-profile__body">
        <aside className="tp-identity" aria-label="선수 핵심 프로필">
          <PlayerPortrait playerId={profile.playerId} nickname={profile.nickname} />
          <PlayerPhotoCredit playerId={profile.playerId} />
          <div className="tp-identity__headline"><span>LEGAL NAME</span><strong>{profile.legalName}</strong></div>
          <dl className="tp-identity__facts">
            <div><dt>국적</dt><dd>{profile.nationality}</dd></div>
            <div><dt>생년월일</dt><dd>{profile.birthDate}</dd></div>
            <div><dt>스냅샷 나이</dt><dd>{profile.ageAtSnapshot}세</dd></div>
            <div><dt>현재 팀</dt><dd>{profile.currentTeamCode}</dd></div>
            <div><dt>포지션</dt><dd>{profile.position}</dd></div>
            <div><dt>계약 잔여일</dt><dd>{profile.contractDaysRemaining}일</dd></div>
          </dl>
          <p className="tp-snapshot-stamp"><span>DATA SNAPSHOT</span><strong>{profile.snapshotAt}</strong>현재 날짜로 재계산하지 않은 값입니다.</p>
        </aside>
        <div className="tp-profile__main">
          <div className="tp-tabs" role="tablist" aria-label="선수 상세 정보">
            {TABS.map((tab, index) => {
              const selected = tab.id === activeTab;
              return <button key={tab.id} ref={(node) => { tabRefs.current[index] = node; }} id={`${instanceId}-tab-${tab.id}`} type="button" role="tab" aria-selected={selected} aria-controls={`${instanceId}-panel-${tab.id}`} tabIndex={selected ? 0 : -1} onClick={() => setActiveTab(tab.id)} onKeyDown={(event) => handleTabKey(event, index)}>{tab.label}</button>;
            })}
          </div>
          <div className="tp-tab-panel" id={`${instanceId}-panel-${activeTab}`} role="tabpanel" tabIndex={0} aria-labelledby={`${instanceId}-tab-${activeTab}`} key={`${profile.playerId}-${activeTab}`}>
            {activeTab === 'ratings' ? <RatingsPanel profile={profile} /> : null}
            {activeTab === 'proficiency' ? <ProficiencyPanel profile={profile} /> : null}
            {activeTab === 'career' ? <CareerPanel profile={profile} /> : null}
            {activeTab === 'contract' ? <ContractPanel profile={profile} /> : null}
          </div>
        </div>
      </div>
    </article>
  );
}
