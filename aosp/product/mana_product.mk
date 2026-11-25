# Optional MANA product fragment.
# Include only after a matching Soong module named ManaHome exists in the AOSP tree.
# This fragment deliberately does not define a device target or claim RMX5566 support.

PRODUCT_PACKAGES += \
    ManaHome

# Keep the AOSP platform's verified boot, SELinux and permission model enabled.
