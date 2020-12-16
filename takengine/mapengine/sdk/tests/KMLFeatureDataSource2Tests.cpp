#include "pch.h"

#include "feature/KMLParser.h"
#include "util/IO2.h"
#include "feature/KmlFeatureDataSource.h"
#include "feature/KMLFeatureDataSource2.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Feature;

namespace takenginetests {

	TEST(KMLFeatureDataSource2Tests, testBigData) {

		int i = 0;

		{
			std::string resource = TAK::Engine::Tests::getResource("BigData.kmz");
			KMLFeatureDataSource2 dataSource;

			TAK::Engine::Feature::FeatureDataSource2::ContentPtr content(nullptr, nullptr);
			TAKErr code = dataSource.parse(content, resource.c_str());

			while ((code = content->moveToNextFeatureSet()) == TE_Ok) {

				TAK::Engine::Port::String featureSetName;
				code = content->getFeatureSetName(featureSetName);
				ASSERT_EQ((int)TE_Ok, (int)code);

				while ((code = content->moveToNextFeature()) == TE_Ok) {

					TAK::Engine::Feature::FeatureDefinition2 *featureDef = nullptr;
					code = content->get(&featureDef);
					ASSERT_EQ((int)TE_Ok, (int)code);
					ASSERT_NE(nullptr, featureDef);

					const char *featureName = nullptr;
					code = featureDef->getName(&featureName);

					FeatureDefinition2::RawData styleData;
					featureDef->getRawStyle(&styleData);

					FeatureDefinition2::RawData geomData;
					featureDef->getRawGeometry(&geomData);
				}


			}
		}


	}

	TEST(KMLFeatureDataSource2Tests, testUSStates) {

		std::string resource = TAK::Engine::Tests::getResource("us_states.kml");

#if 1
		KMLFeatureDataSource2 dataSource;
#else
		KmlFeatureDataSource dataSource;
#endif

		TAK::Engine::Feature::FeatureDataSource2::ContentPtr content(nullptr, nullptr);
		TAKErr code = dataSource.parse(content, resource.c_str());

		const char *featureNames[] = {
			"Hawaii (1959)",
			"Washington (1889)",
			"Montana (1889)",
			"Maine (1820)",
			"North Dakota (1889)",
			"South Dakota (1889)",
			"Wyoming (1890)",
			"Wisconsin (1848)",
			"Idaho (1890)",
			"Vermont (1791)",
			"Minnesota (1858)",
			"Oregon (1859)",
			"New Hampshire (1788)",
			"Iowa (1846)",
			"Massachusetts (1788)",
			"Nebraska (1867)",
			"New York (1788)",
			"Pennsylvania (1787)",
			"Connecticut (1788)",
			"Rhode Island (1790)",
			"New Jersey (1787)",
			"Indiana (1816)",
			"Nevada (1864)",
			"Utah (1896)",
			"California (1850)",
			"Ohio (1803)",
			"Illinois (1818)",
			"Delaware (1787)",
			"West Virginia (1863)",
			"Maryland (1788)",
			"Colorado (1876)",
			"Kentucky (1792)",
			"Kansas (1861)",
			"Virginia (1788)",
			"Missouri (1821)",
			"Arizona (1912)",
			"Oklahoma (1907)",
			"North Carolina (1789)",
			"Tennessee (1796)",
			"Texas (1845)",
			"New Mexico (1912)",
			"Alabama (1819)",
			"Mississippi (1817)",
			"Georgia (1788)",
			"South Carolina (1788)",
			"Arkansas (1836)",
			"Louisiana (1812)",
			"Florida (1845)",
			"Michigan (1837)",
			"Alaska (1959)"
		};

		code = content->moveToNextFeatureSet();

		TAK::Engine::Port::String featureSetName;
		code = content->getFeatureSetName(featureSetName);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ(featureSetName, "US States");

		size_t featureIndex = 0;

		while ((code = content->moveToNextFeature()) == TE_Ok) {

			TAK::Engine::Feature::FeatureDefinition2 *featureDef = nullptr;
			code = content->get(&featureDef);
			ASSERT_EQ((int)TE_Ok, (int)code);
			ASSERT_NE(nullptr, featureDef);

			const char *featureName = nullptr;
			code = featureDef->getName(&featureName);
			ASSERT_EQ((int)TE_Ok, (int)code);
			ASSERT_NE(nullptr, featureName);
			ASSERT_STREQ(featureName, featureNames[featureIndex]);

			++featureIndex;
		}

		code = content->moveToNextFeatureSet();
		ASSERT_EQ((int)TE_Done, (int)code);

	}
}