#!/bin/sh

echo $@

mkdir ../results ../results/1 ../report
echo "Foo" > ../results/1/foo.txt
cp testreport.xml ../report/
