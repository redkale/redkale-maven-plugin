#!/bin/sh

export LC_ALL="zh_CN.UTF-8"

rm -fr redkale-maven-plugin

rm -fr src

git clone https://github.com/redkale/redkale-maven-plugin.git

cp -fr redkale-maven-plugin/src ./

mvn clean
mvn deploy
