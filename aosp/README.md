# MANA Genesis 4.0 AOSP workspace

This is the AOSP integration layer for MANA, not a complete Android source checkout.
See README_RU.md and device/realme/rmx5566/README.md.

The product fragment in product/mana_product.mk is opt-in and must not be included until the Soong module ManaHome has been defined and built. No proprietary device files are fabricated here.
