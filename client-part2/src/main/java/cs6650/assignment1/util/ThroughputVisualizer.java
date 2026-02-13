package cs6650.assignment1.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class ThroughputVisualizer {
    
    private static final Logger logger = LoggerFactory.getLogger(ThroughputVisualizer.class);
    
    public static void createThroughputChart(Map<Long, Integer> throughputData, String title) {
        logger.info("Creating throughput visualization");
        
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(title);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(1200, 600);
            
            ThroughputPanel panel = new ThroughputPanel(throughputData);
            frame.add(panel);
            
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            logger.info("Throughput chart displayed");
        });
    }
    
    private static class ThroughputPanel extends JPanel {
        private final Map<Long, Integer> data;
        private final int padding = 50;
        private final int labelPadding = 25;
        
        public ThroughputPanel(Map<Long, Integer> data) {
            this.data = data;
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int width = getWidth();
            int height = getHeight();
            
            // Draw white background
            g2.setColor(Color.WHITE);
            g2.fillRect(padding, padding, width - 2 * padding, height - 2 * padding);
            
            // Draw axes
            g2.setColor(Color.BLACK);
            g2.drawLine(padding + labelPadding, height - padding - labelPadding, 
                       padding + labelPadding, padding);
            g2.drawLine(padding + labelPadding, height - padding - labelPadding, 
                       width - padding, height - padding - labelPadding);
            
            if (data.isEmpty()) {
                g2.drawString("No data available", width / 2 - 50, height / 2);
                return;
            }
            
            // Find max throughput for scaling
            int maxThroughput = data.values().stream().max(Integer::compare).orElse(1);
            
            // Calculate scale
            double xScale = ((double) width - 2 * padding - labelPadding) / (data.size() - 1);
            double yScale = ((double) height - 2 * padding - labelPadding) / maxThroughput;
            
            // Draw grid lines
            g2.setColor(Color.LIGHT_GRAY);
            int numYGridLines = 10;
            for (int i = 0; i < numYGridLines + 1; i++) {
                int y = height - padding - labelPadding - (int) (i * (height - 2 * padding - labelPadding) / numYGridLines);
                g2.drawLine(padding + labelPadding, y, width - padding, y);
                
                // Y-axis labels
                g2.setColor(Color.BLACK);
                String yLabel = String.valueOf((int) (i * maxThroughput / numYGridLines));
                FontMetrics metrics = g2.getFontMetrics();
                int labelWidth = metrics.stringWidth(yLabel);
                g2.drawString(yLabel, padding + labelPadding - labelWidth - 5, y + 5);
                g2.setColor(Color.LIGHT_GRAY);
            }
            
            // Draw data line
            g2.setColor(Color.BLUE);
            g2.setStroke(new BasicStroke(2f));
            
            int i = 0;
            Integer prevX = null;
            Integer prevY = null;
            
            for (Map.Entry<Long, Integer> entry : data.entrySet()) {
                int x = padding + labelPadding + (int) (i * xScale);
                int y = height - padding - labelPadding - (int) (entry.getValue() * yScale);
                
                if (prevX != null && prevY != null) {
                    g2.drawLine(prevX, prevY, x, y);
                }
                
                // Draw point
                g2.fillOval(x - 3, y - 3, 6, 6);
                
                prevX = x;
                prevY = y;
                i++;
            }
            
            // Draw labels
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Arial", Font.BOLD, 14));
            
            // Y-axis label
            FontMetrics metrics = g2.getFontMetrics();
            String yAxisLabel = "Messages/Second";
            g2.rotate(-Math.PI / 2);
            g2.drawString(yAxisLabel, -(height + metrics.stringWidth(yAxisLabel)) / 2, 
                         padding - labelPadding);
            g2.rotate(Math.PI / 2);
            
            // X-axis label
            String xAxisLabel = "Time (seconds)";
            g2.drawString(xAxisLabel, (width - metrics.stringWidth(xAxisLabel)) / 2, 
                         height - padding + labelPadding);
            
            // Title
            g2.setFont(new Font("Arial", Font.BOLD, 16));
            metrics = g2.getFontMetrics();
            String title = "Throughput Over Time";
            g2.drawString(title, (width - metrics.stringWidth(title)) / 2, padding - 10);
        }
    }
    
    public static void saveChartAsText(Map<Long, Integer> throughputData, String outputPath) {
        logger.info("Saving throughput chart as text to: {}", outputPath);
        
        try (java.io.PrintWriter writer = new java.io.PrintWriter(outputPath)) {
            writer.println("Throughput Over Time");
            writer.println("====================");
            writer.println("Time (seconds), Messages/Second");
            
            for (Map.Entry<Long, Integer> entry : throughputData.entrySet()) {
                double messagesPerSecond = entry.getValue() / 10.0; // Assuming 10-second buckets
                writer.printf("%d, %.2f%n", entry.getKey(), messagesPerSecond);
            }
            
            logger.info("Chart data saved to text file");
        } catch (java.io.IOException e) {
            logger.error("Error saving chart data", e);
        }
    }
}
