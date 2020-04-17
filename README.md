<img src="https://www.artipie.com/logo.svg" width="64px" height="64px"/>

[![Maven Central](https://img.shields.io/maven-central/v/com.artipie/helm-adapter.svg)](https://maven-badges.herokuapp.com/maven-central/com.artipie/helm-adapter)
[![EO principles respected here](https://www.elegantobjects.org/badge.svg)](https://www.elegantobjects.org)

[![Javadoc](http://www.javadoc.io/badge/com.artipie/helm-adapter.svg)](http://www.javadoc.io/doc/com.artipie/helm-adapter)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/artipie/helm-adapter/blob/master/LICENSE.txt)
[![Hits-of-Code](https://hitsofcode.com/github/artipie/helm-adapter)](https://hitsofcode.com/view/github/artipie/helm-adapter)
[![PDD status](http://www.0pdd.com/svg?name=artipie/helm-adapter)](http://www.0pdd.com/p?name=artipie/helm-adapter)

## Helm repository adapter.

General information about chart repository is [here](https://helm.sh/docs/topics/chart_repository/).

Helm Registry stores helm charts in a hierarchy storage structure and provides a function to orchestrate charts form existed charts. The structure is:
```
|- space
  |- chart
    |- version
```

Every space is independent with others. It means the registry can stores same charts (same name with same version) in two spaces.


### Configuration
Before you start `./bin/registry`, you need to wirte a config file which named `config.yaml` in `./bin`.
Config file is explained below:
```yaml
# The port which the server listen to. Change to any port you like.
listen: ":8099"
# A manager is a charts manager. Now we only support `simple` manager.
manager:
  # The name of charts manager.
  name: "simple"
  # The config of current manager.
  parameters:
    # A manager manages all operations of charts. So it is responsible for sync read and write operationgs.
    # The option indicates which locker the manager will use. Currently we provide a `memory` locker.
    resourcelocker: memory
    # A manager can use many storage backends.
    storagedriver: filesystem
    # The option is a parameter of storage driver `filesystem`. See below `Storage Backends`
    rootdirectory: ./data
```

### Usage
After registry running, you can manage the registry by a registy client (in `pkg/rest/v1`) or simply use http APIs.
In `pkg/api/v1/descriptor`, you can find all descriptors of these APIs.

### Orchestration
The registry can orchestrate charts by a json config like:
```
{
    "save":{
        "chart":"chart name",           // new chart name
        "version":"1.0.0",              // new chart version
        "description":"description"     // new chart description
    },
    "configs":{                         // configs is the orchestration configuration of new chart
        "package":{                     // package indicates an original chart which new chart created from
            "independent":true,         // if the original chart is an independent chart, the option is true
            "space":"space name",       // space/chart/version indicate where original chart is stored
            "chart":"chart name",
            "version":"version number"
        },
        "_config": {
            // root chart config, these configs will store in values.yaml of new chart.
        },
        "chartB": {                     // rename original chart as `chartB`
            "package":{
                "independent":true,     // for explaining, we call this original chart as `XChart`
                "space":"space name",
                "chart":"chart name",
                "version":"version number"
            },
            "_config": {
                // chartB config
            },
            "chartD":{
                "package":{
                    "independent":false,  // if independent is false, it means the original chart is a subchart of `XChart`
                    "space":"space name",
                    "chart":"chart name",
                    "version":"version number"
                },
                "_config": {
                    // chartD config
                }
            }
        },
        "chartC": {
            "package":{
                "independent":false,
                "space":"space name",
                "chart":"chart name",
                "version":"version number"
            },
            "_config": {
                // chartC config
            }
        }
    }
}

```

## How to contribute

Fork repository, make changes, send us a pull request. We will review
your changes and apply them to the `master` branch shortly, provided
they don't violate our quality standards. To avoid frustration, before
sending us your pull request please run full Maven build:
