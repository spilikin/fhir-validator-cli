dist:
	gradle clean build
	
release: dist
	$(eval VERSION := $(shell grep "^version=" gradle.properties | cut -d'=' -f2))
	sed -i '' 's/:latest-version:.*/:latest-version: ${VERSION}/' README.adoc
	git commit README.adoc gradle.properties -m "Prepare v${VERSION}" || true
	git push origin
	gh release create ${VERSION}
	gh release upload ${VERSION} app/build/libs/fhir-validator-cli.jar
