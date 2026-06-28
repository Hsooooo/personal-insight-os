ALTER TABLE finance_transactions
    ADD COLUMN cashflow_amount NUMERIC(14,2),
    ADD COLUMN spending_amount NUMERIC(14,2);

UPDATE finance_transactions
SET cashflow_amount = CASE WHEN cashflow_included THEN amount ELSE 0 END,
    spending_amount = CASE WHEN spending_included THEN amount ELSE 0 END;

WITH mobile_payment_by_cycle AS (
    SELECT
        user_id,
        cycle_id,
        COALESCE(SUM(amount), 0) AS mobile_payment_amount
    FROM finance_transactions
    WHERE asset = '소액결제'
      AND flow_type <> '수입'
      AND spending_included = true
    GROUP BY user_id, cycle_id
)
UPDATE finance_transactions tx
SET spending_included = true,
    spending_amount = GREATEST(tx.amount - COALESCE(mp.mobile_payment_amount, 0), 0)
FROM mobile_payment_by_cycle mp
WHERE tx.user_id = mp.user_id
  AND tx.cycle_id IS NOT DISTINCT FROM mp.cycle_id
  AND tx.subcategory = '통신비'
  AND tx.flow_type <> '수입';

UPDATE finance_transactions tx
SET spending_included = true,
    spending_amount = amount
WHERE tx.subcategory = '통신비'
  AND tx.flow_type <> '수입'
  AND tx.spending_amount = 0
  AND NOT EXISTS (
      SELECT 1
      FROM finance_transactions mp
      WHERE mp.user_id = tx.user_id
        AND mp.cycle_id IS NOT DISTINCT FROM tx.cycle_id
        AND mp.asset = '소액결제'
        AND mp.flow_type <> '수입'
        AND mp.spending_included = true
  );

ALTER TABLE finance_transactions
    ALTER COLUMN cashflow_amount SET NOT NULL,
    ALTER COLUMN cashflow_amount SET DEFAULT 0,
    ALTER COLUMN spending_amount SET NOT NULL,
    ALTER COLUMN spending_amount SET DEFAULT 0;
