package application;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class ClockingInSystem extends JFrame {
    private JTextField startField, endField;
    private JLabel dateLabel;
    private DefaultTableModel tableModel;

    private Connection conn;

    public ClockingInSystem() {
        // DB Setup
        connectDB();
        createTable();

        setTitle("Clock-In System");
        setSize(800, 550);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center window
        setLayout(new BorderLayout());
        

        // Header Panel
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(52, 152, 219)); // blue
        headerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        JLabel headerLabel = new JLabel("Clock-In System");
        headerLabel.setForeground(Color.WHITE);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        headerPanel.add(headerLabel);
        add(headerPanel, BorderLayout.NORTH);

        // Input Panel
        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        inputPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        inputPanel.setBackground(new Color(245, 245, 245)); 

        dateLabel = new JLabel("Date: " + LocalDate.now().toString());
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        startField = new JTextField();
        startField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        endField = new JTextField();
        endField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        inputPanel.add(new JLabel("Today:"));
        inputPanel.add(dateLabel);
        inputPanel.add(new JLabel("Start Time (HH:mm):"));
        inputPanel.add(startField);
        inputPanel.add(new JLabel("End Time (HH:mm):"));
        inputPanel.add(endField);

        // Buttons
        JButton addBtn = new JButton("Add Entry");
        styleButton(addBtn);

        JButton calcBtn = new JButton("Calculate Monthly Salary");
        styleButton(calcBtn);

        inputPanel.add(addBtn);
        inputPanel.add(calcBtn);

        add(inputPanel, BorderLayout.WEST);

        // Table Panel 
        tableModel = new DefaultTableModel(new Object[]{"Date","Start","End","Hours","Pay (€)"}, 0);
        JTable table = new JTable(tableModel);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.setRowHeight(25);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Load existing data
        loadData();

        // Button Actions
        addBtn.addActionListener(e -> addEntry());
        calcBtn.addActionListener(e -> calculateMonthlySalary());
    }

    private void styleButton(JButton btn) {
        btn.setBackground(new Color(52, 152, 219));
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private void connectDB() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:workhours.db");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTable() {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS work_hours (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "date TEXT, start_time TEXT, end_time TEXT, hours_worked REAL, pay REAL)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void addEntry() {
        try {
            LocalDate date = LocalDate.now();
            String start = startField.getText().trim();
            String end = endField.getText().trim();

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
            LocalTime startTime = LocalTime.parse(start, fmt);
            LocalTime endTime = LocalTime.parse(end, fmt);

            double hoursWorked = ChronoUnit.MINUTES.between(startTime, endTime) / 60.0;

            double pay = calculatePay(date.getDayOfWeek(), startTime, endTime, hoursWorked);

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO work_hours(date,start_time,end_time,hours_worked,pay) VALUES(?,?,?,?,?)");
            ps.setString(1, date.toString());
            ps.setString(2, start);
            ps.setString(3, end);
            ps.setDouble(4, hoursWorked);
            ps.setDouble(5, pay);
            ps.executeUpdate();

            tableModel.addRow(new Object[]{date, start, end,
                    String.format("%.2f", hoursWorked),
                    String.format("%.2f", pay)});

            startField.setText("");
            endField.setText("");

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error adding entry: " + ex.getMessage());
        }
    }

    private double calculatePay(DayOfWeek day, LocalTime startTime, LocalTime endTime, double totalHours) {
        double weekdayRate = 16.0;
        double satRate = weekdayRate * 1.25; // €20/h
        double sunRate = weekdayRate * 1.33; // €21.33/h
        double nightRate = weekdayRate * 1.25; // for midnight-8am

        double pay = 0.0;

        if (day == DayOfWeek.SATURDAY) {
            pay = totalHours * satRate;
        } else if (day == DayOfWeek.SUNDAY) {
            pay = totalHours * sunRate;
        } else {
            // Weekdays: break down into before/after midnight
            double beforeMidnight = 0.0, afterMidnight = 0.0;
            LocalTime midnight = LocalTime.MIDNIGHT;

            if (endTime.isBefore(startTime)) { // crosses midnight
                beforeMidnight = ChronoUnit.MINUTES.between(startTime, LocalTime.of(23,59)) / 60.0;
                afterMidnight = ChronoUnit.MINUTES.between(midnight, endTime) / 60.0;
            } else {
                beforeMidnight = totalHours;
            }

            pay = beforeMidnight * weekdayRate + afterMidnight * nightRate;
        }
        return pay;
    }

    private void loadData() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM work_hours")) {
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getString("date"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        rs.getDouble("hours_worked"),
                        rs.getDouble("pay")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void calculateMonthlySalary() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT SUM(pay) as total FROM work_hours WHERE strftime('%m', date)=strftime('%m','now')")) {
            if (rs.next()) {
                double total = rs.getDouble("total");
                double afterTax = total * 0.80; // 20% tax
                JOptionPane.showMessageDialog(this,
                        "Gross Salary: €" + String.format("%.2f", total) +
                                "\nAfter 20% Tax: €" + String.format("%.2f", afterTax),
                        "Monthly Salary", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClockingInSystem().setVisible(true));
    }
}

