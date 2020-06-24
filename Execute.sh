#!/bin/bash
java -classpath ".:sqlite-jdbc-3.7.2.jar" DbUser > output.sql
read -p "hit return"
