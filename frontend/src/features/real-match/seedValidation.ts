const SIGNED_LONG_MIN = -(1n << 63n);
const SIGNED_LONG_MAX = (1n << 63n) - 1n;
const CANONICAL_SIGNED_DECIMAL = /^(?:0|-[1-9]\d*|[1-9]\d*)$/;

export function validateCanonicalSignedInt64Seed(value: string): string | null {
  if (!value) return 'seed를 입력해야 경기를 확인할 수 있습니다.';
  if (!CANONICAL_SIGNED_DECIMAL.test(value)) {
    return '공백, + 기호, 선행 0, -0 없이 signed long 십진 문자열로 입력하세요.';
  }
  try {
    const parsed = BigInt(value);
    if (parsed < SIGNED_LONG_MIN || parsed > SIGNED_LONG_MAX) return 'seed가 signed long 범위를 벗어났습니다.';
  } catch {
    return 'seed를 signed long 십진 문자열로 입력하세요.';
  }
  return null;
}
