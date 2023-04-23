dist:
	gradle clean build
	
release: dist
	$(eval VERSION := $(shell grep "^version=" gradle.properties | cut -d'=' -f2))
	gh release create ${VERSION}
	gh release upload ${VERSION} app/build/libs/fhir-validator-cli.jar
