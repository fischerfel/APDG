# APDG
This tool generates an attributed program dependency graph from complete and incomplete Java source code. The graph is stored as a feature vector which can be used for graph representation learning frameworks. Please find our our embedding network for APDGs here (https://github.com/fischerfel/deep-learning-partial-programs/blob/main/README.md)

## Dependencies

The tool applies the following software:

- WALA https://github.com/wala/WALA
- Partial Program Analysis (PPA) https://www.sable.mcgill.ca/ppa/ppa_eclipse.html

It needs to be compiled as a headless Eclipse plugin. Follow the tutorial (https://www.sable.mcgill.ca/ppa/ppa_eclipse.html) about how to setup and run these kind of plugins.

## Data
We also provide a labeled set of Java source code samples that can be used with this tool (https://github.com/fischerfel/TUM-Crypto).

## Research Paper

This tool is part of our IEEE S&P (Oakland) 2017 reaseach paper "Stack Overflow Considered Harmful? The Impact of Copy&Paste on Android Application Security" (https://ieeexplore.ieee.org/abstract/document/7958574) and USENIX Security 2019 research paper "Stack Overflow Considered Helpful! Deep Learning Security Nudges Towards Stronger Cryptography" (https://www.usenix.org/conference/usenixsecurity19/presentation/fischer)
