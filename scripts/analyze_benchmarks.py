#!/usr/bin/env python3
"""
Benchmark Analysis Script

Analyzes benchmark CSV data from all platforms (Kotlin, Java, Flutter, React Native).
Provides statistical comparisons and visualizations for research purposes.

Usage:
    python analyze_benchmarks.py [benchmark_dirs...]

If no directories are specified, looks for benchmark data in default locations.
"""

import argparse
import os
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages


def find_benchmark_dirs(root: Path) -> List[Path]:
    """Find benchmark session directories recursively."""
    sessions = []
    for benchmarks_dir in root.rglob('benchmarks'):
        if benchmarks_dir.is_dir():
            for session in benchmarks_dir.iterdir():
                if session.is_dir():
                    sessions.append(session)
    return sessions


def load_summary(session_dir: Path) -> Optional[pd.DataFrame]:
    """Load summary.csv from a session directory."""
    summary_file = session_dir / 'summary.csv'
    if not summary_file.exists():
        print(f"  Warning: No summary.csv in {session_dir}")
        return None
    
    try:
        df = pd.read_csv(summary_file)
        df['session'] = session_dir.name
        df['source_dir'] = str(session_dir)
        return df
    except Exception as e:
        print(f"  Error loading {summary_file}: {e}")
        return None


def load_raw_data(session_dir: Path) -> Dict[str, pd.DataFrame]:
    """Load all raw CSV files from a session directory."""
    data = {}
    for csv_file in session_dir.glob('*.csv'):
        if csv_file.name == 'summary.csv':
            continue
        
        try:
            df = pd.read_csv(csv_file)
            test_name = csv_file.stem.replace('_', ' ').title()
            data[test_name] = df
        except Exception as e:
            print(f"  Error loading {csv_file}: {e}")
    
    return data


def merge_summaries(sessions: List[Path]) -> pd.DataFrame:
    """Merge summary data from all sessions."""
    dfs = []
    for session in sessions:
        df = load_summary(session)
        if df is not None:
            dfs.append(df)
    
    if not dfs:
        return pd.DataFrame()
    
    return pd.concat(dfs, ignore_index=True)


def compute_comparison_stats(df: pd.DataFrame) -> pd.DataFrame:
    """Compute comparison statistics across platforms."""
    if df.empty:
        return pd.DataFrame()
    
    # Group by platform and test_name
    grouped = df.groupby(['platform', 'test_name']).agg({
        'avg_ms': ['mean', 'std', 'min', 'max'],
        'median_ms': ['mean', 'std'],
        'std_ms': ['mean'],
        'p95_ms': ['mean'],
        'samples': ['sum'],
        'successes': ['sum'],
        'failures': ['sum']
    }).round(2)
    
    # Flatten multi-level columns
    grouped.columns = ['_'.join(col).strip() for col in grouped.columns.values]
    grouped = grouped.reset_index()
    
    return grouped


def plot_platform_comparison(df: pd.DataFrame, output_path: Path):
    """Generate comparison plots across platforms."""
    if df.empty:
        print("No data to plot")
        return
    
    platforms = df['platform'].unique()
    tests = df['test_name'].unique()
    
    with PdfPages(output_path) as pdf:
        # 1. Bar chart: Average execution time per test per platform
        fig, ax = plt.subplots(figsize=(12, 6))
        
        x = np.arange(len(tests))
        width = 0.8 / len(platforms)
        
        for i, platform in enumerate(platforms):
            platform_df = df[df['platform'] == platform]
            values = []
            errors = []
            for test in tests:
                test_df = platform_df[platform_df['test_name'] == test]
                if not test_df.empty:
                    values.append(test_df['avg_ms'].values[0])
                    errors.append(test_df['std_ms'].values[0] if 'std_ms' in test_df else 0)
                else:
                    values.append(0)
                    errors.append(0)
            
            ax.bar(x + i * width, values, width, yerr=errors, label=platform, capsize=3)
        
        ax.set_xlabel('Test')
        ax.set_ylabel('Average Execution Time (ms)')
        ax.set_title('Benchmark Comparison Across Platforms')
        ax.set_xticks(x + width * (len(platforms) - 1) / 2)
        ax.set_xticklabels(tests, rotation=45, ha='right')
        ax.legend()
        ax.grid(axis='y', alpha=0.3)
        plt.tight_layout()
        pdf.savefig(fig)
        plt.close()
        
        # 2. Box plot: Distribution comparison for each test
        for test in tests:
            test_df = df[df['test_name'] == test]
            if test_df.empty:
                continue
            
            fig, ax = plt.subplots(figsize=(10, 5))
            
            data = []
            labels = []
            for platform in platforms:
                p_df = test_df[test_df['platform'] == platform]
                if not p_df.empty:
                    # Use median and percentiles to create box data
                    data.append([
                        p_df['min_ms'].values[0],
                        p_df['p25_ms'].values[0] if 'p25_ms' in p_df else p_df['median_ms'].values[0] * 0.8,
                        p_df['median_ms'].values[0],
                        p_df['p75_ms'].values[0] if 'p75_ms' in p_df else p_df['median_ms'].values[0] * 1.2,
                        p_df['max_ms'].values[0]
                    ])
                    labels.append(platform)
            
            if data:
                bp = ax.boxplot(data, labels=labels, patch_artist=True)
                colors = plt.cm.Set2(np.linspace(0, 1, len(data)))
                for patch, color in zip(bp['boxes'], colors):
                    patch.set_facecolor(color)
            
            ax.set_ylabel('Execution Time (ms)')
            ax.set_title(f'{test} - Platform Comparison')
            ax.grid(axis='y', alpha=0.3)
            plt.tight_layout()
            pdf.savefig(fig)
            plt.close()
        
        # 3. Heatmap: Relative performance
        fig, ax = plt.subplots(figsize=(10, 6))
        
        pivot = df.pivot_table(values='avg_ms', index='test_name', columns='platform', aggfunc='mean')
        
        if not pivot.empty:
            # Normalize by row minimum (best performer = 1.0)
            normalized = pivot.div(pivot.min(axis=1), axis=0)
            
            im = ax.imshow(normalized.values, cmap='RdYlGn_r', aspect='auto', vmin=1.0, vmax=2.0)
            ax.set_xticks(np.arange(len(pivot.columns)))
            ax.set_yticks(np.arange(len(pivot.index)))
            ax.set_xticklabels(pivot.columns)
            ax.set_yticklabels(pivot.index)
            
            # Add text annotations
            for i in range(len(pivot.index)):
                for j in range(len(pivot.columns)):
                    val = normalized.values[i, j]
                    ax.text(j, i, f'{val:.2f}x', ha='center', va='center',
                            color='white' if val > 1.5 else 'black')
            
            ax.set_title('Relative Performance (1.0 = Best)')
            fig.colorbar(im, ax=ax, label='Relative Time')
        
        plt.tight_layout()
        pdf.savefig(fig)
        plt.close()

    print(f"  Charts saved to: {output_path}")


def generate_report(df: pd.DataFrame, output_path: Path):
    """Generate a markdown report with analysis."""
    if df.empty:
        print("No data for report")
        return
    
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write("# Benchmark Analysis Report\n\n")
        f.write(f"**Generated:** {pd.Timestamp.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        
        # Summary statistics
        f.write("## Summary Statistics\n\n")
        f.write("### Overall\n")
        f.write(f"- **Total Samples:** {df['samples'].sum():,}\n")
        f.write(f"- **Platforms:** {', '.join(df['platform'].unique())}\n")
        f.write(f"- **Tests:** {len(df['test_name'].unique())}\n\n")
        
        # Per-platform summary
        f.write("### Per Platform\n\n")
        f.write("| Platform | Tests | Total Samples | Avg Time (ms) | Success Rate |\n")
        f.write("|----------|-------|---------------|---------------|-------------|\n")
        
        for platform in sorted(df['platform'].unique()):
            p_df = df[df['platform'] == platform]
            total_samples = p_df['samples'].sum()
            successes = p_df['successes'].sum()
            failures = p_df['failures'].sum()
            success_rate = (successes / (successes + failures)) * 100 if (successes + failures) > 0 else 0
            avg_time = p_df['avg_ms'].mean()
            
            f.write(f"| {platform} | {len(p_df)} | {total_samples:,} | {avg_time:.2f} | {success_rate:.1f}% |\n")
        
        f.write("\n")
        
        # Per-test comparison
        f.write("### Per Test Comparison\n\n")
        
        for test in sorted(df['test_name'].unique()):
            f.write(f"#### {test}\n\n")
            f.write("| Platform | Avg (ms) | Median (ms) | Std Dev | P95 (ms) | Samples |\n")
            f.write("|----------|----------|-------------|---------|----------|--------|\n")
            
            test_df = df[df['test_name'] == test]
            for _, row in test_df.iterrows():
                f.write(f"| {row['platform']} | {row['avg_ms']:.2f} | {row['median_ms']:.2f} | {row.get('std_ms', 0):.2f} | {row['p95_ms']:.2f} | {row['samples']:,} |\n")
            
            # Best performer
            best = test_df.loc[test_df['avg_ms'].idxmin()]
            f.write(f"\n*Best: **{best['platform']}** at {best['avg_ms']:.2f} ms*\n\n")
        
        # Recommendations
        f.write("## Observations\n\n")
        
        # Find overall best platform
        platform_avg = df.groupby('platform')['avg_ms'].mean()
        best_overall = platform_avg.idxmin()
        f.write(f"1. **Best Overall Performance:** {best_overall} (avg: {platform_avg[best_overall]:.2f} ms)\n")
        
        # Find most consistent (lowest std dev)
        platform_std = df.groupby('platform')['std_ms'].mean()
        most_consistent = platform_std.idxmin() if 'std_ms' in df else best_overall
        f.write(f"2. **Most Consistent:** {most_consistent} (avg std dev: {platform_std[most_consistent]:.2f} ms)\n")
        
        f.write("\n---\n*Report generated by analyze_benchmarks.py*\n")
    
    print(f"  Report saved to: {output_path}")


def main():
    parser = argparse.ArgumentParser(description='Analyze benchmark CSV data from all platforms')
    parser.add_argument('dirs', nargs='*', help='Benchmark session directories to analyze')
    parser.add_argument('-o', '--output', default='benchmark_analysis', help='Output directory/prefix')
    parser.add_argument('--no-plots', action='store_true', help='Skip generating plots')
    args = parser.parse_args()
    
    # Find sessions
    sessions = []
    if args.dirs:
        for d in args.dirs:
            p = Path(d)
            if p.is_dir():
                sessions.append(p)
    else:
        # Look in common locations
        for search_dir in [Path('.'), Path('..'), Path.home() / 'Documents']:
            sessions.extend(find_benchmark_dirs(search_dir))
    
    if not sessions:
        print("No benchmark sessions found. Usage: python analyze_benchmarks.py [benchmark_dirs...]")
        sys.exit(1)
    
    print(f"Found {len(sessions)} session(s):")
    for s in sessions:
        print(f"  - {s}")
    
    # Load and merge data
    print("\nLoading data...")
    df = merge_summaries(sessions)
    
    if df.empty:
        print("No summary data found. Make sure sessions contain summary.csv files.")
        sys.exit(1)
    
    print(f"Loaded {len(df)} test results from {df['platform'].nunique()} platform(s)")
    
    # Save merged data
    output_dir = Path(args.output)
    output_dir.mkdir(exist_ok=True)
    
    merged_path = output_dir / 'merged_summaries.csv'
    df.to_csv(merged_path, index=False)
    print(f"  Merged data saved to: {merged_path}")
    
    # Compute comparison stats
    print("\nComputing statistics...")
    comparison = compute_comparison_stats(df)
    
    if not comparison.empty:
        comparison_path = output_dir / 'comparison_stats.csv'
        comparison.to_csv(comparison_path, index=False)
        print(f"  Comparison stats saved to: {comparison_path}")
    
    # Generate plots
    if not args.no_plots:
        print("\nGenerating plots...")
        try:
            plot_platform_comparison(df, output_dir / 'benchmark_charts.pdf')
        except Exception as e:
            print(f"  Warning: Failed to generate plots: {e}")
    
    # Generate report
    print("\nGenerating report...")
    generate_report(df, output_dir / 'analysis_report.md')
    
    print("\nDone!")


if __name__ == '__main__':
    main()
