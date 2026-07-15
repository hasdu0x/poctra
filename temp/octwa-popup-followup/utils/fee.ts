export type FeeOption = 'recommended' | 'fast' | 'custom';

export function getEffectiveFee(recommendedFee: number, feeOption: FeeOption, customFee: string): number {
  if (feeOption === 'fast') return recommendedFee * 2;
  if (feeOption === 'custom') return Number.parseInt(customFee, 10) || recommendedFee;
  return recommendedFee;
}
