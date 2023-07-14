# Plotting

 Install [Intellij IDEA Ultimate](https://www.jetbrains.com/idea/download/) and the [Kotlin Notebook plugin](https://plugins.jetbrains.com/plugin/16340-kotlin-notebook), copy the data to the [data](data) folder and run the notebooks.

To create HTML pages run:

```bash
make
```

After modifying code in `src`, restart the kernel.

## Export as SVG

Run in developer tools:

```javascript
Plotly.toImage('space.kscience.plotly.Plot@7a54e338', {format: 'svg'}).then(r => console.log(r))
```

Copy output into URL bar.
