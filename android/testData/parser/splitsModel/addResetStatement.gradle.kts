android {
  splits {
    abi {
      include("abi-include-1", "abi-include-2")
    }
    density {
      include("density-include-1", "density-include-2")
    }
  }
}
