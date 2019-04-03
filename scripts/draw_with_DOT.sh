#!/bin/bash

for FILE in *.dot; do
	FILENAME="${FILE%.*}"
	/usr/local/bin/dot -Tpng $FILE -o $FILENAME.png
done

for DATASET in abuse bsbm1m bsbm10m bsbm100m clean_energy_data_uniq conference ctgovdata dblp_large_uniq dbpedia_persondata_en_uniq enelshops foodista insee_geo lubm1m lubm10m lubm100m nasa nobel nobelprizes_uniq watdiv10m watdiv100m; do
	# 6 summaries: 3x2 grid
	montage -density 600 -tile 3x2 -geometry 2500x2500+100+100 $DATASET*no_sat*.png $DATASET-no_sat-grid.png
	montage -density 600 -tile 3x2 -geometry 2500x2500+100+100 `ls $DATASET*sat*.png | grep -v no_sat` $DATASET-sat-grid.png
done