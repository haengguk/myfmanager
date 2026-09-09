import { CareerContractError } from './careerApi.validation.ts';
export interface NegotiationPrice { policyVersion: 'CAREER_NEGOTIATION_PRICE_V2'; pricedOn: string; region: string; marketBasis: string; strength: number; abilityBand: string; annualDemand: number; referenceHash: string; fxPolicyVersion: string }
export function validatePricing(value: unknown): void {
  if (value == null) return;
  const p = value as NegotiationPrice;
  if (p.policyVersion !== 'CAREER_NEGOTIATION_PRICE_V2' || !['LCK','LPL','LEC','LCS','LCP','CBLOL'].includes(p.region) || !Number.isInteger(p.strength) || p.strength < 12 || p.strength > 240 || !Number.isSafeInteger(p.annualDemand) || p.annualDemand <= 0 || !/^\d{4}-\d{2}-\d{2}$/.test(p.pricedOn) || typeof p.marketBasis !== 'string' || typeof p.abilityBand !== 'string' || !/^[a-f0-9]{64}$/.test(p.referenceHash) || p.fxPolicyVersion !== 'GAME_FIXED_FX_V1') throw new CareerContractError('market.pricing');
}
export function pricingLabel(p: NegotiationPrice | null | undefined): string {
  return p ? `현재 기량·${p.region} 시장 가격 V2 · ${p.pricedOn} 기준` : '기존 협상 가격 기준';
}
