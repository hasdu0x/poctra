import { CheckCircle2, SlidersHorizontal, Zap } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { ouToOct } from '@/utils/api';
import type { FeeOption } from '@/utils/fee';

export type { FeeOption } from '@/utils/fee';

interface FeeSelectorProps {
  recommendedFee: number;
  feeOption: FeeOption;
  customFee: string;
  onFeeOptionChange: (option: FeeOption) => void;
  onCustomFeeChange: (value: string) => void;
  disabled?: boolean;
  isPopupMode?: boolean;
}

const OPTIONS = [
  { id: 'recommended' as const, label: 'Auto', icon: CheckCircle2 },
  { id: 'fast' as const, label: 'Fast', icon: Zap },
  { id: 'custom' as const, label: 'Custom', icon: SlidersHorizontal },
];

export function FeeSelector({
  recommendedFee,
  feeOption,
  customFee,
  onFeeOptionChange,
  onCustomFeeChange,
  disabled = false,
  isPopupMode = false,
}: FeeSelectorProps) {
  const fastFee = recommendedFee * 2;
  const customFeeNumber = Number.parseInt(customFee, 10);
  const feeLabel = (option: FeeOption) => {
    if (option === 'fast') return `${ouToOct(fastFee)} OCT`;
    if (option === 'custom') return Number.isFinite(customFeeNumber) && customFeeNumber > 0
      ? `${ouToOct(customFeeNumber)} OCT`
      : 'Set fee';
    return `${ouToOct(recommendedFee)} OCT`;
  };

  return (
    <div className={`poctra-fee-selector ${isPopupMode ? 'is-compact' : ''}`}>
      <div className="poctra-fee-options" role="radiogroup" aria-label="Network fee">
        {OPTIONS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            role="radio"
            aria-checked={feeOption === id}
            className={feeOption === id ? 'is-active' : ''}
            onClick={() => onFeeOptionChange(id)}
            disabled={disabled}
          >
            <Icon aria-hidden="true" />
            <span><b>{label}</b><small>{feeLabel(id)}</small></span>
          </button>
        ))}
      </div>

      {feeOption === 'custom' && (
        <Input
          type="number"
          inputMode="numeric"
          placeholder={`Recommended: ${recommendedFee} OU`}
          value={customFee}
          onChange={(event) => onCustomFeeChange(event.target.value)}
          min="1"
          step="100"
          disabled={disabled}
          className="poctra-mobile-input font-mono"
          aria-label="Custom network fee in OU"
        />
      )}
    </div>
  );
}
