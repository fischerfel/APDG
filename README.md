# APDG
This tool generates an attributed program dependency graph from complete and incomplete Java source code. The graph is stored as a feature vector which can be used for graph representation learning frameworks. Please find our embedding network for APDGs here (https://github.com/fischerfel/deep-learning-partial-programs)

## Dependencies

The tool applies the following software:

- WALA (https://github.com/wala/WALA)
- Partial Program Analysis (PPA) (https://www.sable.mcgill.ca/ppa/ppa_eclipse.html)

It needs to be compiled as a headless Eclipse plugin. Follow the tutorial (https://www.sable.mcgill.ca/ppa/ppa_eclipse.html) which tells you how to setup and run these kind of plugins.

## Data
We also provide a labeled set of Java source code samples that can be used with this tool (https://github.com/fischerfel/TUM-Crypto).
