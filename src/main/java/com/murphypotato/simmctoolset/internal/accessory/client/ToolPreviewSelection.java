package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.PlanVariant;
import com.murphypotato.simmctoolset.internal.accessory.domain.WeaponMode;

public final class ToolPreviewSelection {
    private WeaponMode weapon = WeaponMode.BOW;
    private PlanVariant variant = PlanVariant.EXPECTED;

    public WeaponMode weapon() {
        return weapon;
    }

    public void setWeapon(WeaponMode weapon) {
        this.weapon = weapon == null ? WeaponMode.BOW : weapon;
    }

    public PlanVariant variant() {
        return variant;
    }

    public void setVariant(PlanVariant variant) {
        this.variant = variant == null ? PlanVariant.EXPECTED : variant;
    }
}
