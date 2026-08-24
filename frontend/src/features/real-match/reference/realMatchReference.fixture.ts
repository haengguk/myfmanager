import referenceJson from './real-match-v8.reference.json';
import type { RealMatchV8ReferenceProjection } from './realMatchReference.contract';

// Full REAL_MATCH_RESPONSE_V1이 아니라 승인된 V8 응답의 compact presentation projection이다.
export const realMatchV8ReferenceProjection = referenceJson as unknown as RealMatchV8ReferenceProjection;
