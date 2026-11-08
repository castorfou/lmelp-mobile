"""Tests du package lmelp_mobile."""

import lmelp_mobile as package


def test_package_importable() -> None:
    """Le package s'importe depuis src/."""
    assert package.__name__ == "lmelp_mobile"
