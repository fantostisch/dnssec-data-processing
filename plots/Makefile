.PHONY: clean

NOTEBOOKS=$(wildcard *.ipynb)
HTML=$(NOTEBOOKS:.ipynb=.html)

all: $(HTML)

%.html: %.ipynb
	jupyter nbconvert --to html $<
	sed -i '0,/title>/{s,</title>,</title>\n<script src="./lib/plotly-2.24.1.min.js" charset="utf-8"></script>\n,}' $@

clean:
	rm -f $(HTML)
