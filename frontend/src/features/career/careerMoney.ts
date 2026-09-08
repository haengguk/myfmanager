/** Readable Korean units without rounding away any stored won. */
export function careerMoney(amount: number, currency: string = 'KRW'): string {
  if (currency !== 'KRW') return `${amount.toLocaleString('ko-KR')} 크레딧 (과거 기록)`;
  const absolute = Math.abs(amount);
  const hundredMillion = Math.floor(absolute / 100_000_000);
  const tenThousand = Math.floor((absolute % 100_000_000) / 10_000);
  const remainder = absolute % 10_000;
  const parts = [hundredMillion ? `${hundredMillion.toLocaleString('ko-KR')}억` : '', tenThousand ? `${tenThousand.toLocaleString('ko-KR')}만` : '', remainder ? remainder.toLocaleString('ko-KR') : ''].filter(Boolean);
  return `${amount < 0 ? '-' : ''}${parts.join(' ') || '0'}원`;
}
