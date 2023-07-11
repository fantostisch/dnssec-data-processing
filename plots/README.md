# Plotting

```bash
make
```

After modifying code in `src`, restart the kernel.

## Export as SVG

Run in developer tools:

```javascript
Plotly.toImage('space.kscience.plotly.Plot@7a54e338', {format:'svg'}).then(r => console.log(r))
```

Copy output into URL bar.
