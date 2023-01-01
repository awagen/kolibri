import setuptools

with open("README.md", "r") as fh:
    long_description = fh.read()

# note:  versions with rc like 0.1.2.RC0 are normalized by setuptools to 0.1.2rc0
setuptools.setup(
    name="KolibriFleet",
    version="0.1.0",
    author="awagen",
    author_email="awagen@posteo.net",
    long_description=long_description,
    long_description_content_type="text/markdown",
    url="", #path to github project
    python_requires='>=3.11',
)
