# APDG
This tool generates an attributed program dependency graph from complete and incomplete Java source code. The graph is stored as a feature vector which can be used for graph representation learning frameworks.

## Dependencies

The tool applies the following software:

- WALA https://github.com/wala/WALA
- Partial Program Analysis (PPA) https://www.sable.mcgill.ca/ppa/ppa_eclipse.html

This tool needs to be compiled as a headless Eclipse plugin. Follow the tutorial (https://www.sable.mcgill.ca/ppa/ppa_eclipse.html) about how to setup and run these kind of plugins.
