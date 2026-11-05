import com.terminalfoam.envenom.handler.CoatingMath;

/** Dependency-free checks; compile alongside either version's CoatingMath.java. */
public final class CoatingMathCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (float power : new float[]{0.25F, 0.5F, 1.0F}) {
            for (int multiplier : new int[]{1, 3}) {
                check(CoatingMath.multiplyDuration(CoatingMath.scaleDuration(-1, power), multiplier) == -1,
                        "infinite duration must survive attack strength and lingering multiplication");
            }
        }
        check(CoatingMath.scaleDuration(200, 0.25F) == 50, "ordinary attack scaling");
        check(CoatingMath.scaleDuration(1, 0.25F) == 20, "minimum blade duration");
        check(CoatingMath.multiplyDuration(200, 3) == 600, "ordinary lingering duration");
        check(CoatingMath.multiplyDuration(Integer.MAX_VALUE, 3) == Integer.MAX_VALUE,
                "very long lingering effects must saturate, never become negative");
        check(CoatingMath.scaleDuration(Integer.MAX_VALUE, 1.0F) == Integer.MAX_VALUE,
                "maximum finite duration must stay finite");
        check(CoatingMath.instantAmount(0, 1.0F) == 3.0F, "instant I amount");
        check(CoatingMath.instantAmount(1, 0.5F) == 3.0F, "instant II at half power");
        float previous = 0.0F;
        for (int amplifier = 0; amplifier <= 255; amplifier++) {
            float amount = CoatingMath.instantAmount(amplifier, 1.0F);
            check(Float.isFinite(amount) && amount > 0.0F && amount >= previous,
                    "instant potency must stay positive, finite and monotone at amplifier " + amplifier);
            previous = amount;
        }
        check(CoatingMath.instantAmount(Integer.MAX_VALUE, 1.0F) == CoatingMath.instantAmount(30, 1.0F),
                "oversized amplifiers use the same safety cap as regeneration");
        System.out.println("Coating arithmetic regression checks passed");
    }
}
