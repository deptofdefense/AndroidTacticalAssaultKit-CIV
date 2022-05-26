#define TINYGLTF_IMPLEMENTATION
#define STB_IMAGE_IMPLEMENTATION
#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "tiny_gltf.h"

#define CATCH_CONFIG_MAIN  // This tells Catch to provide a main() - only do this in one cpp file
#include "catch.hpp"

#include <cstdio>
#include <cstdlib>
#include <cassert>
#include <iostream>
#include <sstream>
#include <fstream>

static JsonDocument JsonConstruct(const char* str)
{
  JsonDocument doc;
  JsonParse(doc, str, strlen(str));
  return doc;
}


TEST_CASE("parse-error", "[parse]") {

  tinygltf::Model model;
  tinygltf::TinyGLTF ctx;
  std::string err;
  std::string warn;

  bool ret = ctx.LoadASCIIFromString(&model, &err, &warn, "bora", static_cast<int>(strlen("bora")), /* basedir*/ "");

  REQUIRE(false == ret);

}

TEST_CASE("datauri-in-glb", "[issue-79]") {

  tinygltf::Model model;
  tinygltf::TinyGLTF ctx;
  std::string err;
  std::string warn;

  bool ret = ctx.LoadBinaryFromFile(&model, &err, &warn, "../models/box01.glb");
  if (!err.empty()) {
    std::cerr << err << std::endl;
  }

  REQUIRE(true == ret);
}

TEST_CASE("extension-with-empty-object", "[issue-97]") {

  tinygltf::Model model;
  tinygltf::TinyGLTF ctx;
  std::string err;
  std::string warn;

  bool ret = ctx.LoadASCIIFromFile(&model, &err, &warn, "../models/Extensions-issue97/test.gltf");
  if (!err.empty()) {
    std::cerr << err << std::endl;
  }
  REQUIRE(true == ret);

  REQUIRE(model.extensionsUsed.size() == 1);
  REQUIRE(model.extensionsUsed[0].compare("VENDOR_material_some_ext") == 0);

  REQUIRE(model.materials.size() == 1);
  REQUIRE(model.materials[0].extensions.size() == 1);
  REQUIRE(model.materials[0].extensions.count("VENDOR_material_some_ext") == 1);

  // TODO(syoyo): create temp directory.
  {
    ret = ctx.WriteGltfSceneToFile(&model, "issue-97.gltf", true, true);
    REQUIRE(true == ret);

    tinygltf::Model m;

    // read back serialized glTF
    bool ret = ctx.LoadASCIIFromFile(&m, &err, &warn, "issue-97.gltf");
    if (!err.empty()) {
      std::cerr << err << std::endl;
    }
    REQUIRE(true == ret);

    REQUIRE(m.extensionsUsed.size() == 1);
    REQUIRE(m.extensionsUsed[0].compare("VENDOR_material_some_ext") == 0);

    REQUIRE(m.materials.size() == 1);
    REQUIRE(m.materials[0].extensions.size() == 1);
    REQUIRE(m.materials[0].extensions.count("VENDOR_material_some_ext") == 1);
  }

}

TEST_CASE("invalid-primitive-indices", "[bounds-checking]") {
  tinygltf::Model model;
  tinygltf::TinyGLTF ctx;
  std::string err;
  std::string warn;

  // Loading is expected to fail, but not crash.
  bool ret = ctx.LoadASCIIFromFile(
      &model, &err, &warn,
      "../models/BoundsChecking/invalid-primitive-indices.gltf");
  REQUIRE_THAT(err,
               Catch::Contains("primitive indices accessor out of bounds"));
  REQUIRE_FALSE(ret);
}

TEST_CASE("invalid-buffer-view-index", "[bounds-checking]") {
  tinygltf::Model model;
  tinygltf::TinyGLTF ctx;
  std::string err;
  std::string warn;

  // Loading is expected to fail, but not crash.
  bool ret = ctx.LoadASCIIFromFile(
      &model, &err, &warn,
      "../models/BoundsChecking/invalid-buffer-view-index.gltf");
  REQUIRE_THAT(err, Catch::Contains("accessor[0] invalid bufferView"));
  REQUIRE_FALSE(ret);
}

TEST_CASE("invalid-buffer-index", "[bounds-checking]") {
  tinygltf::Model model;
  tinygltf::TinyGLTF ctx;
  std::string err;
  std::string warn;

  // Loading is expected to fail, but not crash.
  bool ret = ctx.LoadASCIIFromFile(
      &model, &err, &warn,
      "../models/BoundsChecking/invalid-buffer-index.gltf");
  REQUIRE_THAT(
      err, Catch::Contains("image[0] buffer \"1\" not found in the scene."));
  REQUIRE_FALSE(ret);
}

TEST_CASE("glb-invalid-length", "[bounds-checking]") {
  tinygltf::Model model;
  tinygltf::TinyGLTF ctx;
  std::string err;
  std::string warn;

  // This glb has a much longer length than the provided data and should fail
  // initial range checks.
  const unsigned char glb_invalid_length[] = "glTF"
      "\x20\x00\x00\x00" "\x6c\x66\x00\x00"     //
  //  |     version     |     length      |
      "\x02\x00\x00\x00" "\x4a\x53\x4f\x4e{}";  //
  //  |  model length   |   model format  |

  bool ret = ctx.LoadBinaryFromMemory(&model, &err, &warn, glb_invalid_length,
                                      sizeof(glb_invalid_length));
  REQUIRE_THAT(err, Catch::Contains("Invalid glTF binary."));
  REQUIRE_FALSE(ret);
}

TEST_CASE("integer-out-of-bounds", "[bounds-checking]") {
  tinygltf::Model model;
  tinygltf::TinyGLTF ctx;
  std::string err;
  std::string warn;

  // Loading is expected to fail, but not crash.
  bool ret = ctx.LoadASCIIFromFile(
      &model, &err, &warn,
      "../models/BoundsChecking/integer-out-of-bounds.gltf");
  REQUIRE_THAT(err, Catch::Contains("not a positive integer"));
  REQUIRE_FALSE(ret);
}

TEST_CASE("parse-integer", "[bounds-checking]") {
  SECTION("parses valid numbers") {
    std::string err;
    int result = 123;
    CHECK(tinygltf::ParseIntegerProperty(&result, &err, JsonConstruct("{\"zero\" : 0}"), "zero",
                                         true));
    REQUIRE(err == "");
    REQUIRE(result == 0);

    CHECK(tinygltf::ParseIntegerProperty(&result, &err, JsonConstruct("{\"int\": -1234}"), "int",
                                         true));
    REQUIRE(err == "");
    REQUIRE(result == -1234);
  }

  SECTION("detects missing properties") {
    std::string err;
    int result = -1;
    CHECK_FALSE(tinygltf::ParseIntegerProperty(&result, &err, JsonConstruct(""), "int", true));
    REQUIRE_THAT(err, Catch::Contains("'int' property is missing"));
    REQUIRE(result == -1);
  }

  SECTION("handled missing but not required properties") {
    std::string err;
    int result = -1;
    CHECK_FALSE(
        tinygltf::ParseIntegerProperty(&result, &err, JsonConstruct(""), "int", false));
    REQUIRE(err == "");
    REQUIRE(result == -1);
  }

  SECTION("invalid integers") {
    std::string err;
    int result = -1;

    CHECK_FALSE(tinygltf::ParseIntegerProperty(&result, &err, JsonConstruct("{\"int\": 0.5}"),
      "int", true));
    REQUIRE_THAT(err, Catch::Contains("not an integer type"));

    // Excessively large values and NaN aren't allowed either.
    err.clear();
    CHECK_FALSE(tinygltf::ParseIntegerProperty(&result, &err, JsonConstruct("{\"int\": 1e300}"),
      "int", true));
    REQUIRE_THAT(err, Catch::Contains("not an integer type"));

    err.clear();
    {
      JsonDocument o;
      double nan = std::numeric_limits<double>::quiet_NaN();
      tinygltf::JsonAddMember(o, "int", json(nan));
      CHECK_FALSE(tinygltf::ParseIntegerProperty(
        &result, &err, o,
        "int", true));
      REQUIRE_THAT(err, Catch::Contains("not an integer type"));
    }
  }
}

TEST_CASE("parse-unsigned", "[bounds-checking]") {
  SECTION("parses valid unsigned integers") {
    // Use string-based parsing here, using the initializer list syntax doesn't
    // parse 0 as unsigned.
    auto zero_obj = JsonConstruct("{\"zero\": 0}");

    std::string err;
    size_t result = 123;
    CHECK(
        tinygltf::ParseUnsignedProperty(&result, &err, zero_obj, "zero", true));
    REQUIRE(err == "");
    REQUIRE(result == 0);
  }

  SECTION("invalid integers") {
    std::string err;
    size_t result = -1;

    CHECK_FALSE(tinygltf::ParseUnsignedProperty(&result, &err, JsonConstruct("{\"int\": -1234}"),
                                                "int", true));
    REQUIRE_THAT(err, Catch::Contains("not a positive integer"));

    err.clear();
    CHECK_FALSE(tinygltf::ParseUnsignedProperty(&result, &err, JsonConstruct("{\"int\": 0.5}"),
                                                "int", true));
    REQUIRE_THAT(err, Catch::Contains("not a positive integer"));

    // Excessively large values and NaN aren't allowed either.
    err.clear();
    CHECK_FALSE(tinygltf::ParseUnsignedProperty(&result, &err, JsonConstruct("{\"int\": 1e300}"),
                                                "int", true));
    REQUIRE_THAT(err, Catch::Contains("not a positive integer"));

    err.clear();
    {
      JsonDocument o;
      double nan = std::numeric_limits<double>::quiet_NaN();
      tinygltf::JsonAddMember(o, "int", json(nan));
      CHECK_FALSE(tinygltf::ParseUnsignedProperty(
        &result, &err, o,
        "int", true));
      REQUIRE_THAT(err, Catch::Contains("not a positive integer"));
    }
  }
}

TEST_CASE("parse-integer-array", "[bounds-checking]") {
  SECTION("parses valid integers") {
    std::string err;
    std::vector<int> result;
    CHECK(tinygltf::ParseIntegerArrayProperty(&result, &err,
                                              JsonConstruct("{\"x\": [-1, 2, 3]}"), "x", true));
    REQUIRE(err == "");
    REQUIRE(result.size() == 3);
    REQUIRE(result[0] == -1);
    REQUIRE(result[1] == 2);
    REQUIRE(result[2] == 3);
  }

  SECTION("invalid integers") {
    std::string err;
    std::vector<int> result;
    CHECK_FALSE(tinygltf::ParseIntegerArrayProperty(
        &result, &err, JsonConstruct("{\"x\": [-1, 1e300, 3]}"), "x", true));
    REQUIRE_THAT(err, Catch::Contains("not an integer type"));
  }
}

TEST_CASE("pbr-khr-texture-transform", "[material]") {
  tinygltf::Model model;
  tinygltf::TinyGLTF ctx;
  std::string err;
  std::string warn;

  // Loading is expected to fail, but not crash.
  bool ret = ctx.LoadASCIIFromFile(
      &model, &err, &warn,
      "../models/Cube-texture-ext/Cube-textransform.gltf");
  REQUIRE(ret == true);

  REQUIRE(model.materials.size() == 2);
  REQUIRE(model.materials[0].emissiveTexture.extensions.count("KHR_texture_transform") == 1);
  REQUIRE(model.materials[0].emissiveTexture.extensions["KHR_texture_transform"].IsObject());

  tinygltf::Value::Object &texform = model.materials[0].emissiveTexture.extensions["KHR_texture_transform"].Get<tinygltf::Value::Object>();

  REQUIRE(texform.count("scale"));

  REQUIRE(texform["scale"].IsArray());

  // Note: It looks json.hpp parse integer JSON number as integer, not floating point.
  // IsNumber return true either value is int or floating point.
  REQUIRE(texform["scale"].Get(0).IsNumber());
  REQUIRE(texform["scale"].Get(1).IsNumber());

  double scale[2];
  scale[0] = texform["scale"].Get(0).GetNumberAsDouble();
  scale[1] = texform["scale"].Get(1).GetNumberAsDouble();

  REQUIRE(scale[0] == Approx(1.0));
  REQUIRE(scale[1] == Approx(-1.0));

}

TEST_CASE("image-uri-spaces", "[issue-236]") {

  tinygltf::Model model;
  tinygltf::TinyGLTF ctx;
  std::string err;
  std::string warn;

  // Test image file with single spaces.
  bool ret = ctx.LoadASCIIFromFile(&model, &err, &warn, "../models/CubeImageUriSpaces/CubeImageUriSpaces.gltf");
  if (!err.empty()) {
    std::cerr << err << std::endl;
  }

  REQUIRE(true == ret);

  // Test image file with a beginning space, trailing space, and greater than
  // one consecutive spaces.
  ret = ctx.LoadASCIIFromFile(&model, &err, &warn, "../models/CubeImageUriSpaces/CubeImageUriMultipleSpaces.gltf");
  if (!err.empty()) {
    std::cerr << err << std::endl;
  }

  REQUIRE(true == ret);
}

