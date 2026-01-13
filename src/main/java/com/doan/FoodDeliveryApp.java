package com.doan;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.BasicStroke;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.io.Serializable;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

public class FoodDeliveryApp {

    // -------------------- Save File DTO --------------------
    static class DataBundle implements java.io.Serializable {
        Map<String, User> users;
        List<FoodItem> foods;
        List<String> categories;
        List<Order> orders;
        List<String> logs;
        List<Complaint> complaints;
        List<String> restaurants; // list of restaurant names for quick access
    }

    private final String SAVE_FILE = "food_delivery_app_data.bin";

    private void loadData() {
        try (ObjectInputStream ois = new ObjectInputStream(new java.io.FileInputStream(SAVE_FILE))) {
            DataBundle db = (DataBundle) ois.readObject();
            if (db.users != null) users.putAll(db.users);
            if (db.foods != null) foods.addAll(db.foods);
            if (db.categories != null) categories.addAll(db.categories);
            if (db.orders != null) orders.addAll(db.orders);
            if (db.logs != null) logs.addAll(db.logs);
            if (db.complaints != null) complaints.addAll(db.complaints);
            // restaurants is derived, no need to load explicitly
            System.out.println("Data loaded from file.");
        } catch (Exception ex) {
            System.out.println("No saved data found, starting with fresh seed data.");
            seedData();
        }
    }

    private void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new java.io.FileOutputStream(SAVE_FILE))) {
            DataBundle db = new DataBundle();
            db.users = users;
            db.foods = foods;
            db.categories = categories;
            db.orders = orders;
            db.logs = logs;
            db.complaints = complaints;
            // collect restaurants from foods
            db.restaurants = foods.stream()
                .filter(f -> f.restaurantOwner != null)
                .map(f -> f.restaurantOwner)
                .distinct()
                .collect(Collectors.toList());
            oos.writeObject(db);
            System.out.println("Data saved.");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // -------------------- Data models --------------------
    enum Role {
        CUSTOMER, SHIPPER, RESTAURANT, ADMIN, OWNER, ADMINISTRATOR, CUSTOMER_SERVICE
    }

    static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        final String username;
        String password;
        Role role;
        String address = "";
        String phone = "";
        String profileImagePath = null;
        String restaurantName = null;
        String shipperName = null;
        List<String> myCategories = new ArrayList<>();
        boolean isOpen = true; // for restaurants: open/closed status
        // for shippers
        List<Double> shipperRatings = new ArrayList<>();
        List<String> shipperComments = new ArrayList<>();

        User(String u, String p, Role r) {
            username = u;
            password = p;
            role = r;
        }

        @Override
        public String toString() {
            return username + " (" + role + ")";
        }
    }

    static class FoodItem implements Serializable {
        private static final long serialVersionUID = 3L;
        UUID id = UUID.randomUUID();
        String name;
        String description;
        double price;
        double rating; // 0..5, computed from ratings list
        Color colorPreview; // used as image placeholder
        String category;
        String imagePath = null; // path to local image file
        String restaurantOwner = null; // username of restaurant that owns this food
        boolean inStock = true; // stock status
        List<String> variations = new ArrayList<>(); // food variations like sizes, flavors
        Map<String, Double> variationPrices = new HashMap<>(); // additional price for each variation
        // ratings and comments
        List<Double> ratings = new ArrayList<>();
        List<String> comments = new ArrayList<>();

        FoodItem(String name, String desc, double price, double rating, Color colorPreview, String category) {
            this.name = name;
            this.description = desc;
            this.price = price;
            this.rating = rating;
            this.colorPreview = colorPreview;
            this.category = category;
        }

        void updateRating() {
            if (ratings.isEmpty()) {
                rating = 0;
            } else {
                rating = ratings.stream().mapToDouble(d -> d).average().orElse(0);
            }
        }
    }

    static class Complaint implements Serializable {
        private static final long serialVersionUID = 11L;
        UUID id = UUID.randomUUID();
        String author; // username
        Role authorRole;
        String message;
        Date created = new Date();
        String status = "Pending";

        Complaint(String author, Role authorRole, String message) {
            this.author = author;
            this.authorRole = authorRole;
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s (%s): %s [%s]", id.toString().substring(0, 6), author, authorRole, message,
                    status);
        }
    }

    static class OrderItem implements Serializable {
        private static final long serialVersionUID = 3L;
        FoodItem food;
        int qty;
        String variation = "";

        OrderItem(FoodItem f, int q) {
            food = f;
            qty = q;
        }

        OrderItem(FoodItem f, int q, String v) {
            food = f;
            qty = q;
            variation = v;
        }
    }

    enum OrderStatus {
        PLACED, PREPARING, READY_FOR_PICKUP, ACCEPTED_BY_SHIPPER, DELIVERING, DELIVERED, CANCELLED
    }

    static class Message implements Serializable {
        private static final long serialVersionUID = 10L;
        String sender;
        String text;
        Date time = new Date();

        Message(String s, String t) {
            sender = s;
            text = t;
        }
    }

    static class Order implements Serializable {
        private static final long serialVersionUID = 4L;
        UUID id = UUID.randomUUID();
        User customer;
        List<OrderItem> items = new ArrayList<>();
        OrderStatus status = OrderStatus.PLACED;
        double total;
        String addressSnapshot;
        String phoneSnapshot;
        String complaint = null; // customer complaint text
        String note = null; // customer note for restaurant
        String assignedShipper = null; // username of shipper who accepted
        Date created = new Date();

        // chat between shipper and customer
        List<Message> chat = new ArrayList<>();

        // ratings and comments
        Map<FoodItem, Double> foodRatings = new HashMap<>();
        Map<FoodItem, String> foodComments = new HashMap<>();
        Double shipperRating;
        String shipperComment;

        void recalcTotal() {
            total = items.stream().mapToDouble(i -> (i.food.price + i.food.variationPrices.getOrDefault(i.variation, 0.0)) * i.qty).sum();
        }
    }

    // -------------------- In-memory "database" --------------------
    private final Map<String, User> users = new HashMap<>();
    private final List<FoodItem> foods = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<String> logs = new ArrayList<>(); // simple log for admin ops
    private final List<Complaint> complaints = new ArrayList<>();

    // Application state
    private User currentUser = null;
    private final Map<FoodItem, Map<String, Integer>> cart = new HashMap<>();
    private String selectedRestaurant = null; // Track currently selected restaurant
    private boolean isSelectingRestaurant = false; // Flag to prevent unnecessary refreshes

    // -------------------- UI references --------------------
    private JFrame frame;
    private DefaultListModel<String> catListModel;
    private DefaultListModel<String> restListModel;
    private JList<String> restList;
    private JList<String> catList;
    private JPanel itemsPanel; // right side card area
    private JLabel statusLabel;
    private JButton dashboardButton;
    private JButton cartButton;
    private JLabel userLabel;
    private JButton logoutButton;
    private JButton loginBtn;
    private JButton registerBtn;
    private JLabel avatarLabel;
    private JPanel rightPanel;
    private JPanel topPanel;

    // Image cache for performance
    private final Map<String, ImageIcon> imageCache = new HashMap<>();

    private void preloadImages() {
        // Preload images in background thread for better performance
        SwingUtilities.invokeLater(() -> {
            new Thread(() -> {
                // Preload restaurant images
                for (User user : users.values()) {
                    if (user.role == Role.RESTAURANT && user.profileImagePath != null) {
                        loadScaledImageIcon(user.profileImagePath, 120, 120);
                    }
                }
                // Preload food images
                for (FoodItem food : foods) {
                    if (food.imagePath != null) {
                        loadScaledImageIcon(food.imagePath, 180, 120);
                    }
                }
            }).start();
        });
    }

    // -------------------- Constructor & UI build --------------------
    public FoodDeliveryApp() {
        loadData(); // <── use saved data first
        preloadImages(); // Preload images for better startup performance
        SwingUtilities.invokeLater(this::createAndShowGUI);

        // Save automatically when closing window
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveData));
    }

    private void seedData() {
        // seed users
        users.put("admin", new User("admin", "admin123", Role.ADMIN));
        users.put("owner", new User("owner", "owner123", Role.OWNER));
        users.put("shipper", new User("shipper", "shipper123", Role.SHIPPER));
        users.put("administrator", new User("administrator", "adminops123", Role.ADMINISTRATOR));
        users.put("support", new User("support", "support123", Role.CUSTOMER_SERVICE));
        users.put("customer1", new User("customer1", "pass123", Role.CUSTOMER));
        users.put("shipper1", new User("shipper1", "shipper123", Role.SHIPPER));
        User restAccount = new User("pizzahub", "rest123", Role.RESTAURANT);
        restAccount.restaurantName = "Pizza Hub";
        users.put(restAccount.username, restAccount);
        restAccount.myCategories.add("All");
        restAccount.myCategories.add("Pizza");
        restAccount.myCategories.add("Combos");

        // seed categories & foods
        categories.add("All");
        categories.add("Burgers");
        categories.add("Pizza");
        categories.add("Drinks");
        categories.add("Dessert");

        foods.add(new FoodItem("Classic Burger", "Beef patty, lettuce, tomato", 6.99, 4.5, new Color(0xFFB3BA),
                "Burgers"));
        foods.add(new FoodItem("Cheese Burger", "Double cheese", 8.49, 4.7, new Color(0xFFDFBA), "Burgers"));
        foods.add(
                new FoodItem("Margherita Pizza", "Fresh basil & mozzarella", 10.99, 4.6, new Color(0xFFFFE0), "Pizza"));
        foods.add(new FoodItem("Pepperoni Pizza", "Classic pepperoni", 12.50, 4.4, new Color(0xC1FFC1), "Pizza"));
        foods.add(new FoodItem("Coke", "330ml can", 1.50, 4.1, new Color(0xB3E5FC), "Drinks"));
        foods.add(new FoodItem("Chocolate Cake", "Slice of heaven", 4.75, 4.8, new Color(0xE1BEE7), "Dessert"));

        // set restaurant owner for all seeded foods
        for (FoodItem f : foods) {
            f.restaurantOwner = "Pizza Hub";
        }

        // seed a sample order
        Order sample = new Order();
        sample.customer = users.get("customer1");
        sample.items.add(new OrderItem(foods.get(0), 1));
        sample.items.add(new OrderItem(foods.get(4), 2));
        sample.recalcTotal();
        sample.status = OrderStatus.PLACED;
        orders.add(sample);

        logs.add("System seeded with sample data at " + new Date());
    }

    private double parsePrice(String priceText) {
        // Remove commas and parse as double
        return Double.parseDouble(priceText.replace(",", ""));
    }

    private String formatPrice(double price) {
        // Format price with commas for display
        return String.format("%,.0f", price);
    }

    private void createAndShowGUI() {
        // Set sky blue theme
        Color skyBlue = new Color(135, 206, 235); // Sky blue
        Color lightSkyBlue = new Color(173, 216, 230); // Light sky blue

        frame = new JFrame("Food Delivery Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 700);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(skyBlue);

        JPanel topBar = buildTopBar();
        JSplitPane split = new JSplitPane();
        split.setDividerLocation(180);
        split.setResizeWeight(0.3);
        split.setLeftComponent(buildCategoryList());
        split.setRightComponent(buildItemsArea());

        statusLabel = new JLabel(" Welcome! Please login or register to start ordering.                                                                                                                                                                                 HOTLINE: 0919246425");
        statusLabel.setBorder(new EmptyBorder(6, 6, 6, 6));
        statusLabel.setBackground(lightSkyBlue);
        statusLabel.setOpaque(true);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(240, 248, 255));
        mainPanel.add(topBar, BorderLayout.NORTH);
        mainPanel.add(split, BorderLayout.CENTER);
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        frame.getContentPane().add(mainPanel);

        frame.setVisible(true);

        loadCategories();
        refreshItems("All");
    }

    private JPanel buildTopBar() {
        topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(new EmptyBorder(6, 6, 6, 6));
        topPanel.setBackground(new Color(135, 206, 235)); // Sky blue

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.setOpaque(false);
        left.setBackground(new Color(135, 206, 235));
        JLabel title = new JLabel("Food Delivery");
        title.setFont(title.getFont().deriveFont(20f).deriveFont(Font.BOLD));
        title.setForeground(Color.WHITE);
        left.add(title);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.add(Box.createHorizontalGlue()); // push components to right
        right.setOpaque(false);
        right.setBackground(new Color(135, 206, 235));

        rightPanel = right;

        avatarLabel = new JLabel(); // will hold profile image
        avatarLabel.setPreferredSize(new Dimension(32, 32));
        avatarLabel.setBorder(new EmptyBorder(0, 0, 0, 6));
        right.add(avatarLabel);

        userLabel = new JLabel("Not logged in");
        userLabel.setForeground(Color.WHITE);
        right.add(userLabel);

        loginBtn = new JButton("Login");
        loginBtn.setBackground(new Color(0, 191, 255)); // Deep sky blue
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFocusPainted(false);
        loginBtn.setPreferredSize(new Dimension(80, 30));
        loginBtn.addActionListener(e -> showLoginDialog());
        right.add(loginBtn);

        registerBtn = new JButton("Register");
        registerBtn.setBackground(new Color(0, 191, 255));
        registerBtn.setForeground(Color.WHITE);
        registerBtn.setFocusPainted(false);
        registerBtn.setPreferredSize(new Dimension(80, 30));
        registerBtn.addActionListener(e -> showRegisterDialog());
        right.add(registerBtn);

        // NEW: Logout button (hidden by default)
        logoutButton = new JButton("Logout");
            logoutButton.setBackground(new Color(255, 69, 0)); // Red orange
        logoutButton.setForeground(Color.WHITE);
        logoutButton.setFocusPainted(false);
        logoutButton.setPreferredSize(new Dimension(80, 30));
        logoutButton.setVisible(false);
        logoutButton.addActionListener(e -> {
            currentUser = null;
            userLabel.setText("Not logged in");
            avatarLabel.setIcon(null);
            // disable and reset dashboard button label/size
            dashboardButton.setEnabled(false);
            dashboardButton.setText("Setting");
            FontMetrics fmReset = dashboardButton.getFontMetrics(dashboardButton.getFont());
            int resetW = fmReset.stringWidth("Setting") + 40;
            dashboardButton.setPreferredSize(new Dimension(Math.max(resetW, 100), 30));
            logoutButton.setVisible(false); // hide logout
            loginBtn.setVisible(true);
            registerBtn.setVisible(true);
            cartButton.setVisible(false); // hide cart
            statusLabel.setText("You have logged out.");
            rightPanel.invalidate();
            rightPanel.revalidate();
            rightPanel.repaint();
            topPanel.invalidate();
            topPanel.revalidate();
            topPanel.repaint();
            frame.invalidate();
            frame.revalidate();
            frame.repaint();
        });
        right.add(logoutButton);

        cartButton = new JButton("Cart (0)");
        cartButton.setBackground(new Color(34, 139, 34)); // Green
        cartButton.setForeground(Color.WHITE);
        cartButton.setFocusPainted(false);
        cartButton.setPreferredSize(new Dimension(80, 30));
        cartButton.setVisible(false); // Initially hidden
        cartButton.addActionListener(e -> showCartDialog());
        right.add(cartButton);

        // Dashboard/Settings button: label depends on logged-in role
        String dashText = "Setting";
        if (currentUser != null) {
            if (currentUser.role == Role.RESTAURANT) dashText = "Manage Your Restaurant";
            else if (currentUser.role == Role.CUSTOMER) dashText = "Your Orders";
            else if (currentUser.role == Role.SHIPPER) dashText = "View Current Orders";
            else if (currentUser.role == Role.CUSTOMER_SERVICE) dashText = "View Current Complaint";
            else dashText = "Setting";
        }

        dashboardButton = new JButton(dashText);
        dashboardButton.setBackground(new Color(255, 215, 0)); // Gold
        dashboardButton.setForeground(Color.BLACK);
        dashboardButton.setFocusPainted(false);
        dashboardButton.setOpaque(true);
        // size to fit text using FontMetrics
        FontMetrics fm = dashboardButton.getFontMetrics(dashboardButton.getFont());
        int w = fm.stringWidth(dashText) + 40; // padding
        dashboardButton.setPreferredSize(new Dimension(Math.max(w, 100), 30));
        dashboardButton.addActionListener(e -> showDashboard());
        dashboardButton.setEnabled(false);
        right.add(dashboardButton);

        topPanel.add(left, BorderLayout.WEST);
        topPanel.add(right, BorderLayout.EAST);
        return topPanel;
    }

    private JPanel buildCategoryList() {
        // Create tabbed pane with restaurants and categories
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(173, 216, 230)); // Light sky blue
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(Font.BOLD, 16f)); // Bigger tab font

        // Restaurants tab
        JPanel restPanel = new JPanel(new BorderLayout());
        restPanel.setBackground(new Color(173, 216, 230));
        restPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        restListModel = new DefaultListModel<>();
        restList = new JList<>(restListModel);
        restList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        restList.setFixedCellHeight(180); // Reduced height for smaller images
        // populate restaurants
        refreshRestaurantList();

        restList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = restList.getSelectedValue(); // restaurant display name with status
                if (sel != null) {
                    // Strip status from name
                    String restaurantName = sel.replace(" (Open)", "").replace(" (Closed)", "");
                    selectedRestaurant = restaurantName;
                    isSelectingRestaurant = true;

                    // 1) load categories chỉ của nhà hàng được chọn
                    loadCategoriesForRestaurant(restaurantName);

                    // 2) show only its foods (all categories initially)
                    refreshItemsByRestaurant(restaurantName);

                    isSelectingRestaurant = false;
                } else {
                    selectedRestaurant = null;
                    isSelectingRestaurant = true;

                    // nếu không chọn nhà hàng nào: load categories toàn bộ (nguyên hàm hiện tại)
                    loadCategories(); // hàm bạn đã có — rebuild categories từ tất cả foods
                    refreshItems("All");

                    isSelectingRestaurant = false;
                }
            }
        });

        restPanel.add(new JScrollPane(restList), BorderLayout.CENTER);
        tabbedPane.addTab("Restaurants", restPanel);

        // Categories tab
        JPanel catPanel = new JPanel(new BorderLayout());
        catPanel.setBackground(new Color(173, 216, 230));
        catPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        catListModel = new DefaultListModel<>();
        catList = new JList<>(catListModel);
        catList.setFont(catList.getFont().deriveFont(Font.BOLD, 20f)); // Much bigger font for categories
        catList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        catList.setSelectedIndex(0);
        catList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !isSelectingRestaurant) {
                String sel = catList.getSelectedValue();
                if (sel != null) {
                    if (selectedRestaurant != null) {
                        // Filter by both restaurant and category
                        refreshItemsByRestaurantAndCategory(selectedRestaurant, sel);
                    } else {
                        // No restaurant selected, filter by category only
                        refreshItems(sel);
                    }
                }
            }
        });

        catPanel.add(new JScrollPane(catList), BorderLayout.CENTER);

        JButton loadCatBtn = new JButton("Reload App");
        loadCatBtn.addActionListener(e -> {
            selectedRestaurant = null;
            restList.clearSelection();
            refreshRestaurantList();
            loadCategories();
            refreshItems("All");
        });
        catPanel.add(loadCatBtn, BorderLayout.SOUTH);

        tabbedPane.addTab("Categories", catPanel);

        JScrollPane wrapper = new JScrollPane(tabbedPane);
        wrapper.setBorder(BorderFactory.createTitledBorder("Navigation"));

        // Main panel with tabs and reload button below
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(wrapper, BorderLayout.CENTER);

        JButton reloadBtn = new JButton("Reload App");
        reloadBtn.addActionListener(e -> {
            selectedRestaurant = null;
            restList.clearSelection();
            refreshRestaurantList();
            loadCategories();
            refreshItems("All");
        });
        mainPanel.add(reloadBtn, BorderLayout.SOUTH);

        return mainPanel;
    }

    private JScrollPane buildItemsArea() {
        itemsPanel = new JPanel();
        itemsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 14, 14));
        itemsPanel.setBackground(new Color(173, 216, 230)); // Light sky blue
        itemsPanel.setDoubleBuffered(true); // Enable double buffering for smoother rendering
        JScrollPane sp = new JScrollPane(itemsPanel);
        sp.setBorder(BorderFactory.createTitledBorder("Food Menu"));
        return sp;
    }

    private void refreshItems(String categoryFilter) {
        itemsPanel.removeAll();
        List<FoodItem> list = foods.stream()
                .filter(f -> "All".equals(categoryFilter) || f.category.equals(categoryFilter))
                .collect(Collectors.toList());

        for (FoodItem f : list) {
            itemsPanel.add(createFoodCard(f));
        }
        itemsPanel.revalidate();
        itemsPanel.repaint();
    }

    private void refreshRestaurantList() {
        restListModel.clear();
        // gom các tên nhà hàng từ food items
        Set<String> set = new LinkedHashSet<>();
        for (FoodItem f : foods) {
            if (f.restaurantOwner != null) {
                set.add(f.restaurantOwner);
            }
        }
        // đổ vào JList with status
        for (String r : set) {
            User restUser = users.values().stream()
                .filter(u -> u.role == Role.RESTAURANT && r.equals(u.restaurantName))
                .findFirst().orElse(null);
            String status = (restUser != null && restUser.isOpen) ? " (Open)" : " (Closed)";
            restListModel.addElement(r + status);
        }
        // Set custom renderer for colors
        restList.setCellRenderer(new RestaurantListRenderer());
    }

    private void refreshItemsByRestaurant(String restaurantName) {
        if (restaurantName == null) {
            return; // Don't refresh if restaurant name is null
        }
        itemsPanel.removeAll();
        List<FoodItem> list = foods.stream()
                .filter(f -> restaurantName.equals(f.restaurantOwner))
                .collect(Collectors.toList());
        for (FoodItem f : list)
            itemsPanel.add(createFoodCard(f));
        itemsPanel.revalidate();
        itemsPanel.repaint();
    }

    private void refreshItemsByRestaurantAndCategory(String restaurantName, String categoryFilter) {
        if (restaurantName == null || categoryFilter == null) {
            return; // Don't refresh if parameters are null
        }
        itemsPanel.removeAll();
        List<FoodItem> list = foods.stream()
                .filter(f -> restaurantName.equals(f.restaurantOwner))
                .filter(f -> "All".equals(categoryFilter) || categoryFilter.equals(f.category))
                .collect(Collectors.toList());
        for (FoodItem f : list)
            itemsPanel.add(createFoodCard(f));
        itemsPanel.revalidate();
        itemsPanel.repaint();
    }

    private JPanel createFoodCard(FoodItem f) {
        JPanel card = new JPanel(new BorderLayout());
        card.setPreferredSize(new Dimension(200, 280)); // Adjusted width for smaller images
        card.setBorder(BorderFactory.createLineBorder(new Color(135, 206, 235), 2, true)); // Sky blue border
        card.setBackground(new Color(240, 248, 255)); // Alice blue

        // image top: show real image if available, else colored preview
        JLabel imgLabel = new JLabel();
        imgLabel.setPreferredSize(new Dimension(180, 120)); // Reduced size for better performance
        imgLabel.setBorder(new EmptyBorder(6, 6, 6, 6));

        ImageIcon ico = loadScaledImageIcon(f.imagePath, 180, 120); // Reduced size for better performance
        if (ico != null) {
            imgLabel.setIcon(ico);
        } else {
            // fallback: colored box
            JPanel placeholder = new JPanel();
            placeholder.setPreferredSize(new Dimension(180, 120)); // Reduced size for better performance
            placeholder.setBackground(f.colorPreview);
            placeholder.setBorder(new EmptyBorder(6, 6, 6, 6));
            card.add(placeholder, BorderLayout.NORTH);
        }
        // if we set icon, add the label
        if (imgLabel.getIcon() != null) {
            card.add(imgLabel, BorderLayout.NORTH);
        }

        // center: name, price, desc
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(6, 6, 6, 6));
        JLabel name = new JLabel(f.name);
        name.setFont(name.getFont().deriveFont(14f).deriveFont(Font.BOLD));
        center.add(name);
        JLabel price = new JLabel("VND " + formatPrice(f.price));
        price.setFont(price.getFont().deriveFont(16f).deriveFont(Font.BOLD));
        price.setForeground(new Color(34, 139, 34)); // Forest green for price
        center.add(price);
        JLabel rating = new JLabel(String.format("⭐ %.1f (%d reviews)", f.rating, f.ratings.size()));
        center.add(rating);
        JLabel desc = new JLabel("<html>" + f.description + "</html>");
        desc.setFont(desc.getFont().deriveFont(11f)); // Slightly larger than default
        center.add(desc);
        card.add(center, BorderLayout.CENTER);

        // bottom: add to cart + qty
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton add = new JButton("Add");
        add.setBackground(new Color(0, 191, 255));
        add.setForeground(Color.WHITE);
        add.setFocusPainted(false);
        // Check if restaurant is open
        User restUser = users.values().stream()
            .filter(u -> u.role == Role.RESTAURANT && f.restaurantOwner != null && f.restaurantOwner.equals(u.restaurantName))
            .findFirst().orElse(null);
        boolean isOpen = restUser == null || restUser.isOpen;
        boolean canAdd = isOpen && f.inStock;
        add.setEnabled(canAdd);
        add.setText(canAdd ? "Add" : (f.inStock ? "Closed" : "Out of Stock"));
        add.addActionListener(e -> {
            if (!isOpen) {
                JOptionPane.showMessageDialog(frame, "This restaurant is currently closed.");
                return;
            }
            if (!f.inStock) {
                JOptionPane.showMessageDialog(frame, "This item is out of stock.");
                return;
            }
            String selectedVariation = "";
            if (!f.variations.isEmpty()) {
                // Create options with prices, but store variation names separately
                String[] options = new String[f.variations.size()];
                String[] variationNames = f.variations.toArray(new String[0]);
                
                for (int i = 0; i < f.variations.size(); i++) {
                    String v = f.variations.get(i);
                    double variationPrice = f.variationPrices.getOrDefault(v, 0.0);
                    if (variationPrice == 0.0) {
                        options[i] = v; // Just show variation name if price is 0
                    } else {
                        String priceDisplay = (variationPrice > 0 ? "+" : "") + formatPrice(variationPrice);
                        options[i] = v + " " + priceDisplay + " (VND)";
                    }
                }
                
                JComboBox<String> varBox = new JComboBox<>(options);
                int result = JOptionPane.showConfirmDialog(frame, varBox, "Choose variation", JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_OPTION) {
                    int selectedIndex = varBox.getSelectedIndex();
                    selectedVariation = variationNames[selectedIndex]; // Use the actual variation name
                } else {
                    return;
                }
            }
            cart.computeIfAbsent(f, k -> new HashMap<>()).merge(selectedVariation, 1, Integer::sum);
            updateCartButton();
            log(String.format("%s added %s%s to cart", userOrAnon(), f.name, selectedVariation.isEmpty() ? "" : " (" + selectedVariation + ")"));
            statusLabel.setText("Added to cart: " + f.name + (selectedVariation.isEmpty() ? "" : " (" + selectedVariation + ")"));
        });
        bottom.add(add);

        JButton reviewsBtn = new JButton("Reviews");
        reviewsBtn.setBackground(new Color(0, 191, 255));
        reviewsBtn.setForeground(Color.WHITE);
        reviewsBtn.setFocusPainted(false);
        reviewsBtn.addActionListener(e -> showFoodReviewsDialog(f));
        bottom.add(reviewsBtn);

        // If current user is ADMIN allow inline remove (quick demo)
        if (currentUser != null && currentUser.role == Role.ADMIN) {
            JButton remove = new JButton("Remove");
            remove.setBackground(new Color(255, 69, 0)); // Red orange for remove
            remove.setForeground(Color.WHITE);
            remove.setFocusPainted(false);
            remove.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(frame, "Remove " + f.name + "?", "Confirm",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    foods.remove(f);
                    refreshItems(catList.getSelectedValue());
                    log("Admin removed food: " + f.name);
                }
            });
            bottom.add(remove);
        }

        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private void updateCartButton() {
        int totalQty = cart.values().stream().mapToInt(m -> m.values().stream().mapToInt(Integer::intValue).sum()).sum();
        cartButton.setText("Cart (" + totalQty + ")");
    }

    // -------------------- dialogs & dashboards --------------------
    /** CUSTOMER: profile settings — change password, address, phone */
    private void showCustomerSettingsDialog() {
        if (currentUser == null || currentUser.role != Role.CUSTOMER) {
            JOptionPane.showMessageDialog(frame, "Only customers can edit profile.");
            return;
        }

        JPanel p = new JPanel(new GridLayout(0, 1));

        // Current info
        JPasswordField oldPassFld = new JPasswordField();
        JPasswordField newPassFld = new JPasswordField();
        JTextField addrFld = new JTextField(currentUser.address);
        JTextField phoneFld = new JTextField(currentUser.phone);
        JButton changeImgBtn = new JButton("Change Profile Image");
        JLabel imgSelectedLabel = new JLabel(
                currentUser.profileImagePath == null ? "No image" : currentUser.profileImagePath);
        changeImgBtn.addActionListener(ev -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                String pth = chooser.getSelectedFile().getAbsolutePath();
                imgSelectedLabel.setText(chooser.getSelectedFile().getName());
                currentUser.profileImagePath = pth; // temporarily set — will be saved on OK
            }
        });
        p.add(changeImgBtn);
        p.add(imgSelectedLabel);

        p.add(new JLabel("Enter current password:"));
        p.add(oldPassFld);

        p.add(new JLabel("New password:"));
        p.add(newPassFld);

        p.add(new JLabel("Address:"));
        p.add(addrFld);

        p.add(new JLabel("Phone number:"));
        p.add(phoneFld);

        int res = JOptionPane.showConfirmDialog(frame, p, "Profile Settings",
                JOptionPane.OK_CANCEL_OPTION);

        if (res == JOptionPane.OK_OPTION) {
            String oldp = new String(oldPassFld.getPassword());
            String newp = new String(newPassFld.getPassword());

            // verify old password
            if (!currentUser.password.equals(oldp)) {
                JOptionPane.showMessageDialog(frame, "Incorrect current password.");
                return;
            }

            if (!newp.isBlank()) {
                currentUser.password = newp;
            }

            currentUser.address = addrFld.getText().trim();
            currentUser.phone = phoneFld.getText().trim();

            log("Customer updated profile: " + currentUser.username);
            // update top bar avatar
            Component[] rightComps = ((JPanel) ((JPanel) frame.getContentPane().getComponent(0)).getComponent(1))
                    .getComponents();
            for (Component c : rightComps) {
                if (c instanceof JLabel) {
                    ImageIcon ico = loadScaledImageIcon(currentUser.profileImagePath, 32, 32);
                    ((JLabel) c).setIcon(ico);
                    break;
                }
            }

            JOptionPane.showMessageDialog(frame, "Profile updated successfully!");
        }
    }

    private void showLoginDialog() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        JTextField userFld = new JTextField();
        JPasswordField passFld = new JPasswordField();
        p.add(new JLabel("Username:"));
        p.add(userFld);
        p.add(new JLabel("Password:"));
        p.add(passFld);

        int res = JOptionPane.showConfirmDialog(frame, p, "Login", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String u = userFld.getText().trim();
            String pass = new String(passFld.getPassword());
            User found = users.get(u);
            if (found != null && found.password.equals(pass)) {
                currentUser = found;
                if (avatarLabel != null) {
                    ImageIcon ico = loadScaledImageIcon(currentUser.profileImagePath, 32, 32);
                    if (ico != null)
                        avatarLabel.setIcon(ico);
                    else
                        avatarLabel.setIcon(null);
                }
                userLabel.setText("Logged in: " + currentUser.username + " [" + currentUser.role + "]");
                dashboardButton.setEnabled(true);
                // update dashboard button text and size for current role
                String dashText;
                if (currentUser.role == Role.RESTAURANT) dashText = "Manage Your Restaurant";
                else if (currentUser.role == Role.CUSTOMER) dashText = "Your Orders";
                else if (currentUser.role == Role.SHIPPER) dashText = "View Current Orders";
                else if (currentUser.role == Role.CUSTOMER_SERVICE) dashText = "View Current Complaint";
                else dashText = "Setting";
                dashboardButton.setText(dashText);
                FontMetrics fmDash = dashboardButton.getFontMetrics(dashboardButton.getFont());
                int dashW = fmDash.stringWidth(dashText) + 40; // padding
                dashboardButton.setPreferredSize(new Dimension(Math.max(dashW, 100), 30));
                statusLabel.setText("Welcome back, " + currentUser.username + "!");
                log("User logged in: " + currentUser.username);
                refreshForRole();
                // Hide login/register, show logout
                loginBtn.setVisible(false);
                registerBtn.setVisible(false);
                logoutButton.setVisible(true);
                cartButton.setVisible(currentUser.role == Role.CUSTOMER);
                System.out.println("Logout button visible: " + logoutButton.isVisible());
                rightPanel.invalidate();
                rightPanel.revalidate();
                rightPanel.repaint();
                topPanel.invalidate();
                topPanel.revalidate();
                topPanel.repaint();
                frame.invalidate();
                frame.revalidate();
                frame.repaint();
            } else {
                JOptionPane.showMessageDialog(frame, "Invalid username/password");
            }
        }
    }

    private String userOrAnon() {
        return currentUser == null ? "Anonymous" : currentUser.username;
    }

    private void showRegisterDialog() {
        JDialog dlg = new JDialog(frame, "Register", true);
        dlg.setSize(400, 350);
        dlg.setLocationRelativeTo(frame);
        dlg.getContentPane().setBackground(new Color(173, 216, 230)); // Light sky blue

        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(new Color(240, 248, 255));
        p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        // Username
        p.add(new JLabel("Choose username:"), gbc);
        gbc.gridx = 1;
        JTextField userFld = new JTextField(16);
        p.add(userFld, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy++;
        p.add(new JLabel("Choose password:"), gbc);
        gbc.gridx = 1;
        JPasswordField passFld = new JPasswordField(16);
        p.add(passFld, gbc);

        // Role selector
        gbc.gridx = 0;
        gbc.gridy++;
        p.add(new JLabel("Register as:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> roleBox = new JComboBox<>(new String[] { "Customer", "Shipper", "Restaurant" });
        p.add(roleBox, gbc);

        // Shipper name field (hidden unless Shipper chosen)
        gbc.gridx = 0;
        gbc.gridy++;
        JLabel shipperLbl = new JLabel("Shipper name:");
        JTextField shipperFld = new JTextField(16);
        // initially hidden
        shipperLbl.setVisible(false);
        shipperFld.setVisible(false);
        p.add(shipperLbl, gbc);
        gbc.gridx = 1;
        p.add(shipperFld, gbc);

        // Restaurant name field (hidden unless Restaurant chosen)
        gbc.gridx = 0;
        gbc.gridy++;
        JLabel restLbl = new JLabel("Restaurant name:");
        JTextField restFld = new JTextField(16);
        // initially hidden
        restLbl.setVisible(false);
        restFld.setVisible(false);
        p.add(restLbl, gbc);
        gbc.gridx = 1;
        p.add(restFld, gbc);

        // Show/hide fields based on selection
        roleBox.addActionListener(ae -> {
            String sel = (String) roleBox.getSelectedItem();
            boolean isShipper = "Shipper".equals(sel);
            boolean isRest = "Restaurant".equals(sel);
            shipperLbl.setVisible(isShipper);
            shipperFld.setVisible(isShipper);
            restLbl.setVisible(isRest);
            restFld.setVisible(isRest);
            // revalidate parent so changes appear
            p.revalidate();
            p.repaint();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(new Color(240, 248, 255));
        JButton okBtn = new JButton("Register");
        okBtn.setBackground(new Color(0, 191, 255));
        okBtn.setForeground(Color.WHITE);
        okBtn.setFocusPainted(false);
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setBackground(new Color(255, 69, 0));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setFocusPainted(false);

        okBtn.addActionListener(e -> {
            String u = userFld.getText().trim();
            String pass = new String(passFld.getPassword());
            String roleChoice = (String) roleBox.getSelectedItem();

            if (u.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Username and password required.");
                return;
            }
            if (users.containsKey(u)) {
                JOptionPane.showMessageDialog(dlg, "Username already taken.");
                return;
            }

            Role assignedRole = Role.CUSTOMER;
            if ("Shipper".equals(roleChoice))
                assignedRole = Role.SHIPPER;
            else if ("Restaurant".equals(roleChoice))
                assignedRole = Role.RESTAURANT;

            User nu = new User(u, pass, assignedRole);

            // Handle shipper name
            if (assignedRole == Role.SHIPPER) {
                String sname = shipperFld.getText().trim();
                if (sname.isEmpty()) {
                    JOptionPane.showMessageDialog(dlg, "Please provide a shipper name.");
                    return;
                }
                nu.shipperName = sname;
            }

            // Handle restaurant name
            if (assignedRole == Role.RESTAURANT) {
                String rname = restFld.getText().trim();
                if (rname.isEmpty()) {
                    JOptionPane.showMessageDialog(dlg, "Please provide a restaurant name.");
                    return;
                }
                nu.restaurantName = rname;
                nu.myCategories.add("Main");
                nu.myCategories.add("Drinks");
            }

            refreshRestaurantList();
            catListModel.addElement("All"); // only if you need to ensure 'All' exists
            // optional: set some defaults
            nu.address = "";
            nu.phone = "";

            users.put(u, nu);
            log("New user registered: " + u + " role=" + assignedRole
                    + (nu.shipperName == null ? "" : " shipper=" + nu.shipperName)
                    + (nu.restaurantName == null ? "" : " rest=" + nu.restaurantName));
            JOptionPane.showMessageDialog(dlg, "Registered. You can now login.");
            dlg.dispose();
        });

        cancelBtn.addActionListener(e -> dlg.dispose());

        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);

        dlg.setLayout(new BorderLayout());
        dlg.add(p, BorderLayout.CENTER);
        dlg.add(buttonPanel, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void showCartDialog() {
        if (cart.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Cart is empty.");
            return;
        }
        JDialog dlg = new JDialog(frame, "Cart", true);
        dlg.setSize(600, 500);
        dlg.setLocationRelativeTo(frame);
        dlg.getContentPane().setBackground(new Color(173, 216, 230)); // Light sky blue

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(new Color(240, 248, 255));
        DefaultListModel<String> model = new DefaultListModel<>();
        List<FoodItem> tempFoods = new ArrayList<>();
        List<String> tempVars = new ArrayList<>();
        cart.forEach((food, varMap) -> varMap.forEach((var, qty) -> {
            double itemPrice = food.price + food.variationPrices.getOrDefault(var, 0.0);
            model.addElement(qty + " x " + food.name + (var.isEmpty() ? "" : " (" + var + ")") + "  - VND " + formatPrice(itemPrice * qty));
            tempFoods.add(food);
            tempVars.add(var);
        }));
        JList<String> list = new JList<>(model);
        main.add(new JScrollPane(list), BorderLayout.CENTER);

        // Note text area
        JPanel notePanel = new JPanel(new BorderLayout());
        notePanel.setBorder(BorderFactory.createTitledBorder("Note for Restaurant (optional)"));
        JTextArea noteArea = new JTextArea(4, 50);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        JScrollPane noteScroll = new JScrollPane(noteArea);
        noteScroll.setPreferredSize(new Dimension(550, 80));
        notePanel.add(noteScroll, BorderLayout.CENTER);
        main.add(notePanel, BorderLayout.SOUTH);

        double total = cart.entrySet().stream().mapToDouble(e -> {
            FoodItem food = e.getKey();
            return e.getValue().entrySet().stream().mapToDouble(varEntry -> {
                String var = varEntry.getKey();
                int qty = varEntry.getValue();
                double itemPrice = food.price + food.variationPrices.getOrDefault(var, 0.0);
                return itemPrice * qty;
            }).sum();
        }).sum();
        JLabel totalLbl = new JLabel("Total: VND " + formatPrice(total));
        totalLbl.setBorder(new EmptyBorder(6, 6, 6, 6));
        main.add(totalLbl, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton checkout = new JButton("Checkout");
        checkout.setBackground(new Color(0, 191, 255));
        checkout.setForeground(Color.WHITE);
        checkout.setFocusPainted(false);
        checkout.addActionListener(e -> {
            if (currentUser == null || currentUser.role != Role.CUSTOMER) {
                JOptionPane.showMessageDialog(dlg, "You must be logged in as a customer to checkout.");
                return;
            }
            // Payment method choice
            String[] options = {"Cash on Delivery", "Online Payment"};
            int choice = JOptionPane.showOptionDialog(dlg, "Choose payment method:", "Payment Method",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == JOptionPane.CLOSED_OPTION) return; // cancelled

            boolean isCashOnDelivery = (choice == 0);

            // create order
            Order o = new Order();
            o.customer = currentUser;
            cart.forEach((food, varMap) -> varMap.forEach((var, qty) -> o.items.add(new OrderItem(food, qty, var))));
            o.addressSnapshot = currentUser.address;
            o.phoneSnapshot = currentUser.phone;
            String noteText = noteArea.getText().trim();
            if (!noteText.isEmpty()) {
                o.note = noteText;
            }
            o.recalcTotal();
            orders.add(o);
            cart.clear();
            updateCartButton();
            log("Order placed by " + currentUser.username + " orderId=" + o.id + " payment=" + (isCashOnDelivery ? "COD" : "Online"));
            statusLabel.setText("Order placed! Order ID: " + o.id.toString().substring(0, 6));
            dlg.dispose();

            if (isCashOnDelivery) {
                JOptionPane.showMessageDialog(frame, "Order placed!\nID: " + o.id + "\nTotal: VND " + formatPrice(o.total) + "\nPayment: Cash on Delivery");
            } else {
                // Open payment dialog
                JDialog paymentDialog = new JDialog(frame, "Payment", true);
                paymentDialog.setLayout(new BorderLayout());
                // QR Code panel
                JPanel qrPanel = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        // Simple QR code pattern
                        int size = Math.min(getWidth(), getHeight()) - 20;
                        int cellSize = size / 21; // 21x21 grid
                        int offsetX = (getWidth() - size) / 2;
                        int offsetY = (getHeight() - size) / 2;
                        g.setColor(Color.BLACK);
                        // Draw a simple QR-like pattern
                        for (int i = 0; i < 21; i++) {
                            for (int j = 0; j < 21; j++) {
                                // Create a pattern that looks like QR code
                                boolean draw = false;
                                if ((i < 7 && j < 7) || (i < 7 && j > 13) || (i > 13 && j < 7)) {
                                    draw = true; // position squares
                                } else if (i == 2 || j == 2 || i == 18 || j == 18) {
                                    draw = true; // alignment
                                } else if ((i + j) % 3 == 0) {
                                    draw = true; // some pattern
                                }
                                if (draw) {
                                    g.fillRect(offsetX + i * cellSize, offsetY + j * cellSize, cellSize, cellSize);
                                }
                            }
                        }
                    }
                };
                qrPanel.setPreferredSize(new Dimension(200, 200));
                qrPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
                paymentDialog.add(qrPanel, BorderLayout.CENTER);
                // Total label
                JLabel totalLabel = new JLabel("Total: VND " + formatPrice(o.total), SwingConstants.CENTER);
                totalLabel.setFont(new Font("Arial", Font.BOLD, 16));
                totalLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
                paymentDialog.add(totalLabel, BorderLayout.NORTH);
                // Payment done button
                JButton paymentDoneButton = new JButton("Payment Done");
                paymentDoneButton.addActionListener(ev -> paymentDialog.dispose());
                JPanel buttonPanel = new JPanel();
                buttonPanel.add(paymentDoneButton);
                paymentDialog.add(buttonPanel, BorderLayout.SOUTH);
                paymentDialog.pack();
                paymentDialog.setLocationRelativeTo(frame);
                paymentDialog.setVisible(true);
            }
        });
        bottom.add(checkout);

        JButton removeBtn = new JButton("Remove Selected");
        removeBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(dlg, "Please select an item to remove.");
                return;
            }
            FoodItem food = tempFoods.get(idx);
            String var = tempVars.get(idx);
            Map<String, Integer> varMap = cart.get(food);
            if (varMap != null) {
                int current = varMap.get(var);
                if (current > 1) {
                    varMap.put(var, current - 1);
                } else {
                    varMap.remove(var);
                    if (varMap.isEmpty()) {
                        cart.remove(food);
                    }
                }
            }
            updateCartButton();
            // Refresh the dialog
            dlg.dispose();
            if (!cart.isEmpty()) {
                showCartDialog();
            } else {
                JOptionPane.showMessageDialog(frame, "Cart is now empty.");
            }
        });
        bottom.add(removeBtn);

        JButton editAddr = new JButton("Edit address/phone");
        editAddr.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(dlg, "Please select an item first.");
                return;
            }
            FoodItem f = tempFoods.get(idx); // <-- FIX CHÍNH
            if (currentUser != null) {
                boolean isAdmin = currentUser.role == Role.ADMIN;
                boolean isOwner = (currentUser.role == Role.RESTAURANT &&
                        currentUser.restaurantName != null &&
                        currentUser.restaurantName.equals(f.restaurantOwner));
                if (isAdmin || isOwner) {
                    JButton edit = new JButton("Edit");
                    edit.addActionListener(ev -> {
                        // open small edit dialog - name, price, category, image
                        JTextField nameF = new JTextField(f.name);
                        JTextField priceF = new JTextField(String.valueOf(f.price));
                        JTextField ratingF = new JTextField(String.valueOf(f.rating));
                        String[] catArray;
                        if (currentUser != null && currentUser.role == Role.RESTAURANT
                                && !currentUser.myCategories.isEmpty()) {
                            catArray = currentUser.myCategories.toArray(new String[0]);
                        } else {
                            catArray = categories.toArray(new String[0]);
                        }
                        JComboBox<String> catBox = new JComboBox<>(catArray);
                        catBox.setSelectedItem(f.category);
                        JPanel p = new JPanel(new GridLayout(0, 1));
                        p.add(new JLabel("Name:"));
                        p.add(nameF);
                        p.add(new JLabel("Price:"));
                        p.add(priceF);
                        p.add(new JLabel("Rating:"));
                        p.add(ratingF);
                        p.add(new JLabel("Category:"));
                        p.add(catBox);

                        // change image button (optional)
                        JButton changeImg = new JButton("Change Image");
                        JLabel imgLbl = new JLabel(f.imagePath == null ? "No image" : f.imagePath);
                        final String[] newImg = { f.imagePath };
                        changeImg.addActionListener(ae -> {
                            JFileChooser chooser = new JFileChooser();
                            chooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "jpeg"));
                            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                                newImg[0] = chooser.getSelectedFile().getAbsolutePath();
                                imgLbl.setText(chooser.getSelectedFile().getName());
                            }
                        });
                        p.add(changeImg);
                        p.add(imgLbl);

                        int r = JOptionPane.showConfirmDialog(frame, p, "Edit Food", JOptionPane.OK_CANCEL_OPTION);
                        if (r == JOptionPane.OK_OPTION) {
                            try {
                                f.name = nameF.getText().trim();
                                f.price = parsePrice(priceF.getText().trim());
                                f.rating = Double.parseDouble(ratingF.getText().trim());
                                f.category = (String) catBox.getSelectedItem();
                                f.imagePath = newImg[0];
                                refreshItems(catList.getSelectedValue());
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(frame, "Invalid input.");
                            }
                        }
                    });
                    bottom.add(edit);

                    JButton remove = new JButton("Remove");
                    remove.addActionListener(ev -> {
                        int confirm = JOptionPane.showConfirmDialog(frame, "Remove " + f.name + "?", "Confirm",
                                JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            foods.remove(f);
                            refreshItems(catList.getSelectedValue());
                            log("Removed food " + f.name);
                        }
                    });
                    bottom.add(remove);
                }
            }
        });

        // add Edit button to bottom and show dialog
        bottom.add(editAddr);

        notePanel.add(bottom, BorderLayout.SOUTH);
        main.add(notePanel, BorderLayout.SOUTH);
        dlg.getContentPane().add(main);
        dlg.setVisible(true);
    }

    private void showDashboard() {
        if (currentUser == null) {
            JOptionPane.showMessageDialog(frame, "Login required.");
            return;
        }
        switch (currentUser.role) {
            case ADMIN:
                showAdminPanel();
                break;
            case OWNER:
                showOwnerPanel();
                break;
            case SHIPPER:
                showShipperPanel();
                break;
            case ADMINISTRATOR:
                showAdministratorPanel();
                break;
            case CUSTOMER_SERVICE:
                showCustomerServicePanel();
                break;
            case CUSTOMER:
                showCustomerPanel();
                break;
            case RESTAURANT:
                showRestaurantPanel();
                break;
        }
    }

    private ImageIcon loadScaledImageIcon(String path, int w, int h) {
        if (path == null || path.isBlank())
            return null;

        // Create cache key with path and dimensions
        String cacheKey = path + "_" + w + "x" + h;

        // Check cache first
        ImageIcon cached = imageCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            File f = new File(path);
            if (!f.exists() || !f.canRead())
                return null;

            // Use ImageIO for better error handling
            Image img = javax.imageio.ImageIO.read(f);
            if (img == null)
                return null;

            Image scaled = img.getScaledInstance(w, h, Image.SCALE_FAST);
            ImageIcon icon = new ImageIcon(scaled);

            // Cache the scaled image
            imageCache.put(cacheKey, icon);
            return icon;
        } catch (Exception ex) {
            // Silently ignore image loading errors
            return null;
        }
    }

    // Admin: add/remove categories & foods, and manage users
    private void showAdminPanel() {
        JDialog dlg = new JDialog(frame, "Admin Panel", true);
        dlg.setSize(900, 600);
        dlg.setLocationRelativeTo(frame);

        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: Menu Management
        JPanel menuPanel = createMenuManagementPanel(dlg);
        tabbedPane.addTab("Menu Management", menuPanel);

        // Tab 2: User Management
        JPanel userPanel = createUserManagementPanel(dlg);
        tabbedPane.addTab("User Management", userPanel);

        // Tab 3: Order Management
        JPanel orderPanel = createAdminOrderManagementPanel(dlg);
        tabbedPane.addTab("Order Management", orderPanel);

        dlg.getContentPane().add(tabbedPane);
        dlg.setVisible(true);
    }

    private JPanel createMenuManagementPanel(JDialog dlg) {
        JPanel main = new JPanel(new BorderLayout());

        // Left: list categories
        DefaultListModel<String> catModel = new DefaultListModel<>();
        categories.forEach(catModel::addElement);
        JList<String> cats = new JList<>(catModel);
        cats.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        main.add(new JScrollPane(cats), BorderLayout.WEST);

        // Right: foods list with add/remove
        DefaultListModel<String> foodModel = new DefaultListModel<>();
        foods.forEach(f -> foodModel.addElement(f.category + " - " + f.name + " (VND " + formatPrice(f.price) + ")"));
        JList<String> foodList = new JList<>(foodModel);
        main.add(new JScrollPane(foodList), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addCat = new JButton("Add Category");
        addCat.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(dlg, "Category name:");
            if (name != null && !name.isBlank()) {
                categories.add(name);
                catModel.addElement(name);
                catListModel.addElement(name); // update main UI
                log("Admin added category: " + name);
            }
        });
        bottom.add(addCat);

        JButton addFood = new JButton("Add Food");
        addFood.addActionListener(e -> {
            JTextField nameF = new JTextField();
            JTextArea descF = new JTextArea(3, 20);
            JScrollPane descScroll = new JScrollPane(descF);
            JTextField priceF = new JTextField();
            JTextField ratingF = new JTextField("4.0");
            JComboBox<String> catBox = new JComboBox<>(categories.toArray(new String[0]));
            JPanel p = new JPanel(new GridLayout(0, 1));
            p.add(new JLabel("Name:"));
            p.add(nameF);
            p.add(new JLabel("Description:"));
            p.add(descScroll);
            p.add(new JLabel("Price:"));
            p.add(priceF);
            p.add(new JLabel("Rating (0..5):"));
            p.add(ratingF);
            p.add(new JLabel("Category:"));
            p.add(catBox);
            JButton chooseImg = new JButton("Choose Image");
            JLabel chosenLbl = new JLabel("No image selected");
            final String[] chosenPath = { null };

            chooseImg.addActionListener(ae2 -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "jpeg"));
                if (chooser.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                    chosenPath[0] = chooser.getSelectedFile().getAbsolutePath();
                    chosenLbl.setText(chooser.getSelectedFile().getName());
                }
            });

            p.add(new JLabel("Food Image:"));
            p.add(chooseImg);
            p.add(chosenLbl);
            refreshRestaurantList();

            if (JOptionPane.showConfirmDialog(dlg, p, "Add Food",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                try {
                    String name = nameF.getText().trim();
                    String desc = descF.getText().trim();
                    double pr = parsePrice(priceF.getText().trim());
                    double r = Double.parseDouble(ratingF.getText().trim());
                    String c = (String) catBox.getSelectedItem();
                    FoodItem nf = new FoodItem(name, desc, pr, r, randomPastelColor(), c);
                    nf.imagePath = chosenPath[0]; // set selected image path (may be null)
                    foods.add(nf);
                    foodModel.addElement(nf.category + " - " + nf.name + " (VND " + formatPrice(nf.price) + ")");
                    refreshItems(catList.getSelectedValue());
                    log("Admin added food: " + nf.name + (nf.imagePath == null ? "" : " (with image)"));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dlg, "Invalid input.");
                }
            }
        });
        bottom.add(addFood);

        JButton removeFood = new JButton("Remove Selected Food");
        removeFood.addActionListener(e -> {
            int idx = foodList.getSelectedIndex();
            if (idx >= 0) {
                FoodItem f = foods.get(idx);
                int c = JOptionPane.showConfirmDialog(dlg, "Remove " + f.name + "?", "Confirm",
                        JOptionPane.YES_NO_OPTION);
                if (c == JOptionPane.YES_OPTION) {
                    foods.remove(f);
                    foodModel.remove(idx);
                    refreshItems(catList.getSelectedValue());
                    log("Admin removed food: " + f.name);
                }
            }
        });
        bottom.add(removeFood);
        refreshRestaurantList();

        JButton changeImg = new JButton("Change Image for Selected");
        changeImg.addActionListener(ev -> {
            int idx = foodList.getSelectedIndex();
            if (idx < 0)
                return;
            FoodItem f = foods.get(idx);
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                f.imagePath = chooser.getSelectedFile().getAbsolutePath();
                log("Admin changed image for " + f.name);
                refreshItems(catList.getSelectedValue());
                JOptionPane.showMessageDialog(dlg, "Image updated for " + f.name);
            }
        });
        bottom.add(changeImg);

        main.add(bottom, BorderLayout.SOUTH);
        return main;
    }

    private JPanel createUserManagementPanel(JDialog dlg) {
        JPanel main = new JPanel(new BorderLayout());

        // List of users
        DefaultListModel<String> userModel = new DefaultListModel<>();
        users.values().forEach(u -> userModel.addElement(u.username + " - " + u.role));
        JList<String> userList = new JList<>(userModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        main.add(new JScrollPane(userList), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton addUser = new JButton("Add User");
        addUser.addActionListener(e -> showAddEditUserDialog(dlg, null, userModel));
        bottom.add(addUser);

        JButton editUser = new JButton("Edit Selected User");
        editUser.addActionListener(e -> {
            int idx = userList.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(dlg, "Please select a user first.");
                return;
            }
            String selected = userModel.get(idx);
            String uname = selected.split(" - ")[0];
            User u = users.get(uname);
            if (u != null) {
                showAddEditUserDialog(dlg, u, userModel);
            }
        });
        bottom.add(editUser);

        JButton deleteUser = new JButton("Delete Selected User");
        deleteUser.addActionListener(e -> {
            int idx = userList.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(dlg, "Please select a user first.");
                return;
            }
            String selected = userModel.get(idx);
            String uname = selected.split(" - ")[0];
            User u = users.get(uname);
            if (u != null) {
                int confirm = JOptionPane.showConfirmDialog(dlg, "Delete user " + uname + "?", "Confirm",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    users.remove(uname);
                    logs.removeIf(entry -> entry.contains(uname));
                    userModel.remove(idx);
                    log("Admin deleted user: " + uname);
                    JOptionPane.showMessageDialog(dlg, "User deleted.");
                }
            }
        });
        bottom.add(deleteUser);

        main.add(bottom, BorderLayout.SOUTH);
        return main;
    }

    private void showAddEditUserDialog(JDialog parent, User editingUser, DefaultListModel<String> userModel) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        // Username
        p.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        JTextField userFld = new JTextField(editingUser != null ? editingUser.username : "", 16);
        if (editingUser != null) userFld.setEditable(false); // can't change username
        p.add(userFld, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy++;
        p.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        JTextField passFld = new JTextField(editingUser != null ? editingUser.password : "", 16);
        p.add(passFld, gbc);

        // Role
        gbc.gridx = 0;
        gbc.gridy++;
        p.add(new JLabel("Role:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> roleBox = new JComboBox<>(new String[] { "CUSTOMER", "SHIPPER", "RESTAURANT", "ADMIN", "OWNER", "ADMINISTRATOR", "CUSTOMER_SERVICE" });
        if (editingUser != null) roleBox.setSelectedItem(editingUser.role.toString());
        p.add(roleBox, gbc);

        // Address
        gbc.gridx = 0;
        gbc.gridy++;
        p.add(new JLabel("Address:"), gbc);
        gbc.gridx = 1;
        JTextField addrFld = new JTextField(editingUser != null ? editingUser.address : "", 16);
        p.add(addrFld, gbc);

        // Phone
        gbc.gridx = 0;
        gbc.gridy++;
        p.add(new JLabel("Phone:"), gbc);
        gbc.gridx = 1;
        JTextField phoneFld = new JTextField(editingUser != null ? editingUser.phone : "", 16);
        p.add(phoneFld, gbc);

        // Profile Image
        gbc.gridx = 0;
        gbc.gridy++;
        p.add(new JLabel("Profile Image:"), gbc);
        gbc.gridx = 1;
        JButton chooseImgBtn = new JButton("Choose Image");
        JLabel imgLbl = new JLabel(editingUser != null && editingUser.profileImagePath != null ? editingUser.profileImagePath : "No image");
        final String[] imgPath = { editingUser != null ? editingUser.profileImagePath : null };
        chooseImgBtn.addActionListener(ae -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                imgPath[0] = chooser.getSelectedFile().getAbsolutePath();
                imgLbl.setText(chooser.getSelectedFile().getName());
            }
        });
        JPanel imgPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        imgPanel.add(chooseImgBtn);
        imgPanel.add(imgLbl);
        p.add(imgPanel, gbc);

        // Restaurant Name (if role is RESTAURANT)
        gbc.gridx = 0;
        gbc.gridy++;
        JLabel restLbl = new JLabel("Restaurant Name:");
        JTextField restFld = new JTextField(editingUser != null ? editingUser.restaurantName : "", 16);
        restLbl.setVisible(false);
        restFld.setVisible(false);
        p.add(restLbl, gbc);
        gbc.gridx = 1;
        p.add(restFld, gbc);

        // Show/hide restaurant field based on role
        roleBox.addActionListener(ae -> {
            String sel = (String) roleBox.getSelectedItem();
            boolean isRest = "RESTAURANT".equals(sel);
            restLbl.setVisible(isRest);
            restFld.setVisible(isRest);
            p.revalidate();
            p.repaint();
        });
        if (editingUser != null && editingUser.role == Role.RESTAURANT) {
            restLbl.setVisible(true);
            restFld.setVisible(true);
        }

        int res = JOptionPane.showConfirmDialog(parent, p, editingUser == null ? "Add User" : "Edit User",
                JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String uname = userFld.getText().trim();
            String pass = passFld.getText();
            String roleStr = (String) roleBox.getSelectedItem();
            Role role = Role.valueOf(roleStr);
            String addr = addrFld.getText().trim();
            String phone = phoneFld.getText().trim();

            if (editingUser == null) {
                // Add new user
                if (uname.isEmpty() || pass.isEmpty()) {
                    JOptionPane.showMessageDialog(parent, "Username and password required.");
                    return;
                }
                if (users.containsKey(uname)) {
                    JOptionPane.showMessageDialog(parent, "Username already taken.");
                    return;
                }
                User nu = new User(uname, pass, role);
                nu.address = addr;
                nu.phone = phone;
                nu.profileImagePath = imgPath[0];
                if (role == Role.RESTAURANT) {
                    nu.restaurantName = restFld.getText().trim();
                    nu.myCategories.add("All");
                    nu.myCategories.add("Main");
                }
                users.put(uname, nu);
                userModel.addElement(nu.username + " - " + nu.role);
                log("Admin added user: " + uname + " role=" + role);
                JOptionPane.showMessageDialog(parent, "User added.");
            } else {
                // Edit existing user
                editingUser.password = pass;
                editingUser.role = role;
                editingUser.address = addr;
                editingUser.phone = phone;
                editingUser.profileImagePath = imgPath[0];
                if (role == Role.RESTAURANT) {
                    editingUser.restaurantName = restFld.getText().trim();
                } else {
                    editingUser.restaurantName = null;
                    editingUser.myCategories.clear();
                }
                userModel.set(userModel.indexOf(editingUser.username + " - " + editingUser.role), editingUser.username + " - " + editingUser.role);
                log("Admin edited user: " + editingUser.username);
                JOptionPane.showMessageDialog(parent, "User updated.");
            }
        }
    }

    private JPanel createAdminOrderManagementPanel(JDialog dlg) {
        JPanel main = new JPanel(new BorderLayout());

        // List of all orders
        DefaultListModel<String> orderModel = new DefaultListModel<>();
        orders.forEach(order -> orderModel.addElement(formatOrderLine(order)));
        JList<String> orderList = new JList<>(orderModel);
        orderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        main.add(new JScrollPane(orderList), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton viewDetailsBtn = new JButton("View Details");
        viewDetailsBtn.addActionListener(e -> {
            int idx = orderList.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(dlg, "Please select an order first.");
                return;
            }
            Order selectedOrder = orders.get(idx);
            showOrderDetailsDialog(dlg, selectedOrder);
        });
        bottom.add(viewDetailsBtn);

        JButton deleteOrderBtn = new JButton("Delete Order");
        deleteOrderBtn.addActionListener(e -> {
            int idx = orderList.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(dlg, "Please select an order first.");
                return;
            }
            Order selectedOrder = orders.get(idx);
            int confirm = JOptionPane.showConfirmDialog(dlg, "Delete order " + selectedOrder.id + "?", "Confirm",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                orders.remove(idx);
                orderModel.remove(idx);
                log("Admin deleted order: " + selectedOrder.id);
                JOptionPane.showMessageDialog(dlg, "Order deleted.");
            }
        });
        bottom.add(deleteOrderBtn);

        main.add(bottom, BorderLayout.SOUTH);
        return main;
    }

    private void showOrderDetailsDialog(JDialog parent, Order o) {
        StringBuilder sb = new StringBuilder();
        sb.append("Order ID: ").append(o.id).append("\n");
        sb.append("Status: ").append(o.status).append("\n");
        sb.append("Ordered at: ").append(o.created).append("\n");
        sb.append("Address: ").append(o.addressSnapshot).append("\n");
        sb.append("Phone: ").append(o.phoneSnapshot).append("\n\n");
        sb.append("Items Ordered:\n");
        for (OrderItem item : o.items) {
            double itemPrice = item.food.price + item.food.variationPrices.getOrDefault(item.variation, 0.0);
            String variationText = item.variation.isEmpty() ? "" : " (" + item.variation + ")";
            sb.append("- ").append(item.food.name).append(variationText).append(" x").append(item.qty).append("  VND ").append(formatPrice(itemPrice * item.qty)).append("\n");
        }
        sb.append("\nTotal: VND ").append(String.format("%.2f", o.total)).append("\n");

        if (o.note != null && !o.note.isEmpty()) {
            sb.append("\nCustomer Note: ").append(o.note).append("\n");
        }

        if (o.assignedShipper != null) {
            User shipper = users.get(o.assignedShipper);
            if (shipper != null) {
                sb.append("\nShipper Information:\n");
                sb.append("Name: ").append(shipper.username).append("\n");
                sb.append("Phone: ").append(shipper.phone != null ? shipper.phone : "N/A").append("\n");
                sb.append("Rating: ").append(String.format("%.1f", shipper.shipperRatings.stream().mapToDouble(d -> d).average().orElse(0))).append(" (").append(shipper.shipperRatings.size()).append(" reviews)\n");

                if (o.status == OrderStatus.DELIVERING) {
                    sb.append("\n🚚 Delivery Information:\n");
                    sb.append("Shipper is currently delivering your order.\n");
                    sb.append("Contact shipper: ").append(shipper.phone != null ? shipper.phone : "N/A").append("\n");
                    sb.append("Estimated delivery: Soon\n");
                }
            }
        } else {
            sb.append("\nNo shipper assigned yet.\n");
        }

        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(500, 400));
        JOptionPane.showMessageDialog(parent, sp, "Order Details", JOptionPane.PLAIN_MESSAGE);
    }

    private void showVirtualMapDialog(Order o) {
        JDialog dlg = new JDialog(frame, "Track Shipper - Order " + o.id, true);
        dlg.setSize(600, 500);
        dlg.setLocationRelativeTo(frame);

        JPanel mapPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawVirtualMap(g, getWidth(), getHeight(), o);
            }
        };
        mapPanel.setBackground(Color.WHITE);
        dlg.getContentPane().add(mapPanel);
        dlg.setVisible(true);
    }

    private void drawVirtualMap(Graphics g, int width, int height, Order o) {
        // Draw background (sky)
        g.setColor(new Color(135, 206, 235)); // Sky blue
        g.fillRect(0, 0, width, height);

        // Draw roads (zigzag pattern)
        g.setColor(Color.GRAY);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(8)); // Thicker roads

        // Horizontal zigzag roads
        int[] yLevels = {120, 270, 420};
        for (int y : yLevels) {
            int x = 0;
            while (x < width) {
                int nextX = Math.min(x + 100 + (int)(Math.random() * 50), width);
                int nextY = y + (int)(Math.random() * 40 - 20); // Slight vertical variation
                g2d.drawLine(x, y, nextX, nextY);
                x = nextX;
                y = nextY;
            }
        }

        // Vertical zigzag roads
        int[] xLevels = {120, 270, 420};
        for (int x : xLevels) {
            int y = 0;
            while (y < height) {
                int nextY = Math.min(y + 100 + (int)(Math.random() * 50), height);
                int nextX = x + (int)(Math.random() * 40 - 20); // Slight horizontal variation
                g2d.drawLine(x, y, nextX, nextY);
                x = nextX;
                y = nextY;
            }
        }

        // Reset stroke
        g2d.setStroke(new BasicStroke());

        // Draw yellow center lines on roads (simplified)
        g.setColor(Color.YELLOW);
        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5, 5}, 0));
        // For simplicity, draw some dashed lines along main roads
        g2d.drawLine(0, 120, width, 120);
        g2d.drawLine(0, 270, width, 270);
        g2d.drawLine(0, 420, width, 420);
        g2d.drawLine(120, 0, 120, height);
        g2d.drawLine(270, 0, 270, height);
        g2d.drawLine(420, 0, 420, height);
        g2d.setStroke(new BasicStroke()); // Reset

        // Draw houses
        g.setColor(Color.ORANGE);
        // Some houses around
        int[][] housePositions = {
            {50, 50}, {200, 50}, {350, 50}, {500, 50},
            {50, 200}, {350, 200}, {500, 200},
            {50, 350}, {200, 350}, {500, 350},
            {50, 500}, {200, 500}, {350, 500}, {500, 500}
        };
        for (int[] pos : housePositions) {
            if (pos[0] < width - 30 && pos[1] < height - 30) {
                // House body
                g.fillRect(pos[0], pos[1], 25, 20);
                // Roof
                g.setColor(Color.RED);
                int[] xPoints = {pos[0], pos[0] + 12, pos[0] + 25};
                int[] yPoints = {pos[1], pos[1] - 10, pos[1]};
                g.fillPolygon(xPoints, yPoints, 3);
                g.setColor(Color.ORANGE);
            }
        }

        // Simulate customer location (a house)
        int customerX = width / 2;
        int customerY = height / 2;
        g.setColor(Color.BLUE);
        g.fillRect(customerX - 10, customerY - 10, 20, 15); // House
        g.setColor(Color.RED);
        int[] roofX = {customerX - 10, customerX, customerX + 10};
        int[] roofY = {customerY - 10, customerY - 20, customerY - 10};
        g.fillPolygon(roofX, roofY, 3);
        g.setColor(Color.BLACK);
        g.drawString("Your House", customerX - 25, customerY - 25);

        // Simulate shipper location (moving van or something)
        int shipperX = customerX + (int)(Math.random() * 200 - 100);
        int shipperY = customerY + (int)(Math.random() * 200 - 100);
        g.setColor(Color.GREEN);
        g.fillRect(shipperX - 8, shipperY - 5, 16, 10); // Van body
        g.setColor(Color.BLACK);
        g.fillOval(shipperX - 10, shipperY + 5, 4, 4); // Wheels
        g.fillOval(shipperX + 6, shipperY + 5, 4, 4);
        g.drawString("🚚 Shipper", shipperX - 20, shipperY - 10);

        // Draw a path line (dotted)
        g.setColor(Color.RED);
        Stroke dashed = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);
        g2d.setStroke(dashed);
        g2d.drawLine(customerX, customerY, shipperX, shipperY);
        g2d.setStroke(new BasicStroke()); // Reset

        // Add some labels
        g.setColor(Color.BLACK);
        g.drawString("Virtual Town Map - Shipper is delivering!", 10, 20);
        g.drawString("Estimated arrival: Soon", 10, 40);
    }

    private void showRestaurantPanel() {
        if (currentUser == null || currentUser.role != Role.RESTAURANT) {
            JOptionPane.showMessageDialog(frame, "Only restaurant accounts can manage a menu.");
            return;
        }
        JDialog dlg = new JDialog(frame, "Restaurant Panel - " + currentUser.restaurantName, true);
        dlg.setSize(900, 600);
        dlg.setLocationRelativeTo(frame);
        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: Menu Management
        JPanel menuPanel = createRestaurantMenuManagementPanel(dlg);
        tabbedPane.addTab("Menu", menuPanel);

        // Tab 2: Order Management
        JPanel orderPanel = createOrderManagementPanel(dlg);
        tabbedPane.addTab("Orders", orderPanel);

        // Tab 3: Settings
        JPanel settingsPanel = createRestaurantSettingsPanel(dlg);
        tabbedPane.addTab("Settings", settingsPanel);

        dlg.getContentPane().add(tabbedPane);
        dlg.setVisible(true);
    }

    private JPanel createRestaurantSettingsPanel(JDialog dlg) {
        JPanel main = new JPanel(new BorderLayout());
        main.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel statusLabel = new JLabel("Restaurant Status: " + (currentUser.isOpen ? "OPEN" : "CLOSED"));
        statusLabel.setFont(statusLabel.getFont().deriveFont(16f).deriveFont(Font.BOLD));
        statusLabel.setForeground(currentUser.isOpen ? Color.GREEN : Color.RED);
        main.add(statusLabel, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(0, 1, 10, 10));
        JButton toggleBtn = new JButton(currentUser.isOpen ? "Close Restaurant" : "Open Restaurant");
        toggleBtn.setBackground(new Color(0, 191, 255));
        toggleBtn.setForeground(Color.WHITE);
        toggleBtn.setFocusPainted(false);
        toggleBtn.addActionListener(e -> {
            currentUser.isOpen = !currentUser.isOpen;
            statusLabel.setText("Restaurant Status: " + (currentUser.isOpen ? "OPEN" : "CLOSED"));
            statusLabel.setForeground(currentUser.isOpen ? Color.GREEN : Color.RED);
            toggleBtn.setText(currentUser.isOpen ? "Close Restaurant" : "Open Restaurant");
            // Update the restaurant list display
            refreshRestaurantList();
            // Refresh items if this restaurant is selected
            if (restList.getSelectedValue() != null && restList.getSelectedValue().replace(" (Open)", "").replace(" (Closed)", "").equals(currentUser.restaurantName)) {
                refreshItemsByRestaurant(currentUser.restaurantName);
            }
            log("Restaurant " + currentUser.username + " " + (currentUser.isOpen ? "opened" : "closed") + " their restaurant");
        });
        center.add(toggleBtn);

        JButton changeImgBtn = new JButton("Change Restaurant Logo");
        changeImgBtn.setBackground(new Color(0, 191, 255));
        changeImgBtn.setForeground(Color.WHITE);
        changeImgBtn.setFocusPainted(false);
        changeImgBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                currentUser.profileImagePath = chooser.getSelectedFile().getAbsolutePath();
                refreshRestaurantList(); // Update the list to show new image
                log("Restaurant " + currentUser.username + " changed profile image");
                JOptionPane.showMessageDialog(dlg, "Restaurant image updated!");
            }
        });
        center.add(changeImgBtn);
        main.add(center, BorderLayout.CENTER);

        return main;
    }

    private JPanel createRestaurantMenuManagementPanel(JDialog dlg) {
        JPanel main = new JPanel(new BorderLayout());
        main.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Food list with custom renderer
        DefaultListModel<String> model = new DefaultListModel<>();
        List<FoodItem> myFoods = foods.stream()
                .filter(f -> currentUser.restaurantName != null && currentUser.restaurantName.equals(f.restaurantOwner))
                .collect(Collectors.toList());
        myFoods.forEach(f -> model.addElement(f.name + " - VND " + formatPrice(f.price) + (f.inStock ? "" : " (Out of Stock)")));

        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new FoodListRenderer(myFoods));
        list.setFixedCellHeight(120); // Increased height for bigger 100x100 images
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(400, 400));

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        buttonsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton addBtn = new JButton("Add Food");
        addBtn.setBackground(new Color(34, 139, 34));
        addBtn.setForeground(Color.WHITE);
        addBtn.setFocusPainted(false);
        addBtn.setPreferredSize(new Dimension(150, 40));

        JButton editBtn = new JButton("Edit Selected Food");
        editBtn.setBackground(new Color(0, 191, 255));
        editBtn.setForeground(Color.WHITE);
        editBtn.setFocusPainted(false);
        editBtn.setPreferredSize(new Dimension(150, 40));

        JButton removeBtn = new JButton("Remove Selected Food");
        removeBtn.setBackground(new Color(255, 69, 0));
        removeBtn.setForeground(Color.WHITE);
        removeBtn.setFocusPainted(false);
        removeBtn.setPreferredSize(new Dimension(150, 40));

        JButton stockBtn = new JButton("Add Stock/Remove Stock Selected Food");
        stockBtn.setBackground(new Color(255, 215, 0));
        stockBtn.setForeground(Color.BLACK);
        stockBtn.setFocusPainted(false);
        stockBtn.setPreferredSize(new Dimension(150, 40));

        buttonsPanel.add(addBtn);
        buttonsPanel.add(editBtn);
        buttonsPanel.add(removeBtn);
        buttonsPanel.add(stockBtn);

        // Category management panel
        JPanel catPanel = new JPanel(new BorderLayout());
        catPanel.setBorder(BorderFactory.createTitledBorder("Categories"));
        catPanel.setPreferredSize(new Dimension(200, 200));

        DefaultListModel<String> catModel = new DefaultListModel<>();
        currentUser.myCategories.forEach(catModel::addElement);
        JList<String> catList = new JList<>(catModel);
        catList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane catScroll = new JScrollPane(catList);

        JPanel catButtons = new JPanel(new FlowLayout());
        JButton addCatBtn = new JButton("Add");
        JButton editCatBtn = new JButton("Edit");
        JButton delCatBtn = new JButton("Remove");
        catButtons.add(addCatBtn);
        catButtons.add(editCatBtn);
        catButtons.add(delCatBtn);

        catPanel.add(catScroll, BorderLayout.CENTER);
        catPanel.add(catButtons, BorderLayout.SOUTH);

        // Right panel combining buttons and categories
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(buttonsPanel, BorderLayout.NORTH);
        rightPanel.add(catPanel, BorderLayout.CENTER);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, rightPanel);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.6);
        main.add(splitPane, BorderLayout.CENTER);

        // Action listeners
        addBtn.addActionListener(e -> {
            JDialog addDialog = new JDialog(dlg, "Add New Food", true);
            addDialog.setSize(500, 600);
            addDialog.setLocationRelativeTo(dlg);

            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JTextField nameField = new JTextField(20);
            JTextArea descArea = new JTextArea(3, 20);
            JScrollPane descScroll = new JScrollPane(descArea);
            JTextField priceField = new JTextField(10);
            JTextField variationsField = new JTextField(20); // New field for variations
            JComboBox<String> catBox = new JComboBox<>(currentUser.myCategories.toArray(new String[0]));
            JButton chooseImgBtn = new JButton("Choose Image");
            JLabel imgLabel = new JLabel("No image selected");
            final String[] imgPath = {null};

            chooseImgBtn.addActionListener(ae -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "jpeg"));
                if (chooser.showOpenDialog(addDialog) == JFileChooser.APPROVE_OPTION) {
                    imgPath[0] = chooser.getSelectedFile().getAbsolutePath();
                    imgLabel.setText(new File(imgPath[0]).getName());
                }
            });

            gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Name:"), gbc);
            gbc.gridx = 1; panel.add(nameField, gbc);

            gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Description:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1.0;
            panel.add(descScroll, gbc);
            gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("Price (VND):"), gbc);
            gbc.gridx = 1; panel.add(priceField, gbc);

            gbc.gridx = 0; gbc.gridy = 3; panel.add(new JLabel("Category:"), gbc);
            gbc.gridx = 1; panel.add(catBox, gbc);

            gbc.gridx = 0; gbc.gridy = 4; panel.add(new JLabel("Variations:"), gbc);
            gbc.gridx = 1; panel.add(variationsField, gbc);

            JButton addVariationButton = new JButton("Add Variation");
            addVariationButton.addActionListener(ae -> {
                JTextField varNameField = new JTextField();
                JTextField varPriceField = new JTextField("0");
                JPanel varPanel = new JPanel(new GridLayout(0, 2));
                varPanel.add(new JLabel("Variation Name:"));
                varPanel.add(varNameField);
                varPanel.add(new JLabel("Additional Price (VND):"));
                varPanel.add(varPriceField);
                int result = JOptionPane.showConfirmDialog(addDialog, varPanel, "Add Variation", JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_OPTION) {
                    String varName = varNameField.getText().trim();
                    String varPriceStr = varPriceField.getText().trim();
                    if (!varName.isEmpty()) {
                        try {
                            double varPrice = parsePrice(varPriceStr);
                            String current = variationsField.getText().trim();
                            String newVar = varName + ":" + varPrice;
                            if (!current.isEmpty()) {
                                variationsField.setText(current + ", " + newVar);
                            } else {
                                variationsField.setText(newVar);
                            }
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(addDialog, "Invalid price");
                        }
                    }
                }
            });
            gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; panel.add(addVariationButton, gbc);

            gbc.gridx = 0; gbc.gridy = 6; panel.add(new JLabel("Image:"), gbc);
            gbc.gridx = 1; panel.add(chooseImgBtn, gbc);

            gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2; panel.add(imgLabel, gbc);

            JPanel buttonPanel = new JPanel(new FlowLayout());
            JButton okBtn = new JButton("Add");
            JButton cancelBtn = new JButton("Cancel");
            buttonPanel.add(okBtn);
            buttonPanel.add(cancelBtn);

            addDialog.add(panel, BorderLayout.CENTER);
            addDialog.add(buttonPanel, BorderLayout.SOUTH);

            okBtn.addActionListener(ae -> {
                try {
                    String name = nameField.getText().trim();
                    String desc = descArea.getText().trim();
                    double price = parsePrice(priceField.getText().trim());
                    String cat = (String) catBox.getSelectedItem();
                    FoodItem fi = new FoodItem(name, desc, price, 0, randomPastelColor(), cat);
                    fi.restaurantOwner = currentUser.restaurantName;
                    fi.imagePath = imgPath[0];
                    String variationsText = variationsField.getText().trim();
                    if (!variationsText.isEmpty()) {
                        String[] varParts = variationsText.split(",");
                        for (String part : varParts) {
                            String trimmed = part.trim();
                            if (trimmed.contains(":")) {
                                String[] namePrice = trimmed.split(":", 2);
                                String varName = namePrice[0].trim();
                                double varPrice = 0;
                                if (namePrice.length > 1) {
                                    try {
                                        varPrice = parsePrice(namePrice[1].trim());
                                    } catch (Exception ex) {
                                        // ignore invalid price, set to 0
                                    }
                                }
                                fi.variations.add(varName);
                                fi.variationPrices.put(varName, varPrice);
                            } else {
                                // old format, assume price 0
                                fi.variations.add(trimmed);
                                fi.variationPrices.put(trimmed, 0.0);
                            }
                        }
                    }
                    foods.add(fi);
                    myFoods.add(fi);
                    model.addElement(fi.name + " - VND " + fi.price);
                    list.setCellRenderer(new FoodListRenderer(myFoods));
                    list.revalidate();
                    list.repaint();
                    refreshItems("All");
                    refreshRestaurantList();
                    log("Restaurant " + currentUser.username + " added food: " + name);
                    addDialog.dispose();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(addDialog, "Invalid price");
                }
            });

            cancelBtn.addActionListener(ae -> addDialog.dispose());

            addDialog.setVisible(true);
        });

        editBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(dlg, "Please select a food item to edit");
                return;
            }
            FoodItem fi = myFoods.get(idx);

            JDialog editDialog = new JDialog(dlg, "Edit Food", true);
            editDialog.setSize(500, 600);
            editDialog.setLocationRelativeTo(dlg);

            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JTextField nameField = new JTextField(fi.name, 20);
            JTextArea descArea = new JTextArea(fi.description, 3, 20);
            JScrollPane descScroll = new JScrollPane(descArea);
            JTextField priceField = new JTextField(String.valueOf(fi.price), 10);
            JTextField variationsField = new JTextField(fi.variations.isEmpty() ? "" : fi.variations.stream().map(v -> v + ":" + fi.variationPrices.getOrDefault(v, 0.0)).collect(Collectors.joining(", ")), 20);
            JComboBox<String> catBox = new JComboBox<>(currentUser.myCategories.toArray(new String[0]));
            catBox.setSelectedItem(fi.category);
            JButton chooseImgBtn = new JButton("Choose Image");
            JLabel imgLabel = new JLabel(fi.imagePath != null ? new File(fi.imagePath).getName() : "No image selected");
            final String[] imgPath = {fi.imagePath};

            chooseImgBtn.addActionListener(ae -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "jpeg"));
                if (chooser.showOpenDialog(editDialog) == JFileChooser.APPROVE_OPTION) {
                    imgPath[0] = chooser.getSelectedFile().getAbsolutePath();
                    imgLabel.setText(new File(imgPath[0]).getName());
                }
            });

            gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Name:"), gbc);
            gbc.gridx = 1; panel.add(nameField, gbc);

            gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Description:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1.0;
            panel.add(descScroll, gbc);
            gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("Price (VND):"), gbc);
            gbc.gridx = 1; panel.add(priceField, gbc);

            gbc.gridx = 0; gbc.gridy = 3; panel.add(new JLabel("Category:"), gbc);
            gbc.gridx = 1; panel.add(catBox, gbc);

            gbc.gridx = 0; gbc.gridy = 4; panel.add(new JLabel("Variations:"), gbc);
            gbc.gridx = 1; panel.add(variationsField, gbc);

            JButton addVariationButton = new JButton("Add Variation");
            addVariationButton.addActionListener(ae -> {
                JTextField varNameField = new JTextField();
                JTextField varPriceField = new JTextField("0");
                JPanel varPanel = new JPanel(new GridLayout(0, 2));
                varPanel.add(new JLabel("Variation Name:"));
                varPanel.add(varNameField);
                varPanel.add(new JLabel("Additional Price (VND):"));
                varPanel.add(varPriceField);
                int result = JOptionPane.showConfirmDialog(editDialog, varPanel, "Add Variation", JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_OPTION) {
                    String varName = varNameField.getText().trim();
                    String varPriceStr = varPriceField.getText().trim();
                    if (!varName.isEmpty()) {
                        try {
                            double varPrice = parsePrice(varPriceStr);
                            String current = variationsField.getText().trim();
                            String newVar = varName + ":" + varPrice;
                            if (!current.isEmpty()) {
                                variationsField.setText(current + ", " + newVar);
                            } else {
                                variationsField.setText(newVar);
                            }
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(editDialog, "Invalid price");
                        }
                    }
                }
            });
            gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; panel.add(addVariationButton, gbc);

            gbc.gridx = 0; gbc.gridy = 6; panel.add(new JLabel("Image:"), gbc);
            gbc.gridx = 1; panel.add(chooseImgBtn, gbc);

            gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2; panel.add(imgLabel, gbc);

            JPanel buttonPanel = new JPanel(new FlowLayout());
            JButton okBtn = new JButton("Update");
            JButton cancelBtn = new JButton("Cancel");
            buttonPanel.add(okBtn);
            buttonPanel.add(cancelBtn);

            editDialog.add(panel, BorderLayout.CENTER);
            editDialog.add(buttonPanel, BorderLayout.SOUTH);

            okBtn.addActionListener(ae -> {
                try {
                    fi.name = nameField.getText().trim();
                    fi.description = descArea.getText().trim();
                    fi.price = parsePrice(priceField.getText().trim());
                    fi.category = (String) catBox.getSelectedItem();
                    String variationsText = variationsField.getText().trim();
                    fi.variations.clear();
                    fi.variationPrices.clear();
                    if (!variationsText.isEmpty()) {
                        String[] varParts = variationsText.split(",");
                        for (String part : varParts) {
                            String trimmed = part.trim();
                            if (trimmed.contains(":")) {
                                String[] namePrice = trimmed.split(":", 2);
                                String varName = namePrice[0].trim();
                                double varPrice = 0;
                                if (namePrice.length > 1) {
                                    try {
                                        varPrice = parsePrice(namePrice[1].trim());
                                    } catch (Exception ex) {
                                        // ignore invalid price, set to 0
                                    }
                                }
                                fi.variations.add(varName);
                                fi.variationPrices.put(varName, varPrice);
                            } else {
                                // old format, assume price 0
                                fi.variations.add(trimmed);
                                fi.variationPrices.put(trimmed, 0.0);
                            }
                        }
                    }
                    fi.imagePath = imgPath[0];
                    model.set(idx, fi.name + " - VND " + formatPrice(fi.price));
                    list.setCellRenderer(new FoodListRenderer(myFoods));
                    list.revalidate();
                    list.repaint();
                    refreshItems("All");
                    refreshRestaurantList();
                    log("Restaurant " + currentUser.username + " edited food: " + fi.name);
                    editDialog.dispose();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(editDialog, "Invalid price");
                }
            });

            cancelBtn.addActionListener(ae -> editDialog.dispose());

            editDialog.setVisible(true);
        });

        removeBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(dlg, "Please select a food item to remove");
                return;
            }
            FoodItem fi = myFoods.get(idx);
            int confirm = JOptionPane.showConfirmDialog(dlg, "Remove '" + fi.name + "'?", "Confirm Removal", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                foods.remove(fi);
                myFoods.remove(idx);
                model.remove(idx);
                list.setCellRenderer(new FoodListRenderer(myFoods));
                list.revalidate();
                list.repaint();
                refreshItems("All");
                refreshRestaurantList();
                log("Restaurant " + currentUser.username + " removed food: " + fi.name);
            }
        });

        stockBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(dlg, "Please select a food item to toggle stock");
                return;
            }
            FoodItem fi = myFoods.get(idx);
            fi.inStock = !fi.inStock;
            model.set(idx, fi.name + " - VND " + fi.price + (fi.inStock ? "" : " (Out of Stock)"));
            list.revalidate();
            list.repaint();
            refreshItems("All");
            log("Restaurant " + currentUser.username + " toggled stock for: " + fi.name + " to " + (fi.inStock ? "in stock" : "out of stock"));
        });

        // Category action listeners
        addCatBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(dlg, "New category name:");
            if (name != null && !name.trim().isEmpty()) {
                name = name.trim();
                if (!currentUser.myCategories.contains(name)) {
                    currentUser.myCategories.add(name);
                    catModel.addElement(name);
                    refreshItemsByRestaurant(currentUser.restaurantName);
                } else {
                    JOptionPane.showMessageDialog(dlg, "Category already exists");
                }
            }
        });

        editCatBtn.addActionListener(e -> {
            int idx = catList.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(dlg, "Please select a category to edit");
                return;
            }
            String oldName = catModel.get(idx);
            String newName = JOptionPane.showInputDialog(dlg, "New name:", oldName);
            if (newName != null && !newName.trim().isEmpty()) {
                newName = newName.trim();
                currentUser.myCategories.set(idx, newName);
                catModel.set(idx, newName);
                // Update foods with this category
                for (FoodItem fi : foods) {
                    if (currentUser.restaurantName.equals(fi.restaurantOwner) && oldName.equals(fi.category)) {
                        fi.category = newName;
                    }
                }
                refreshItemsByRestaurant(currentUser.restaurantName);
            }
        });

        delCatBtn.addActionListener(e -> {
            int idx = catList.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(dlg, "Please select a category to remove");
                return;
            }
            String name = catModel.get(idx);
            if ("All".equals(name)) {
                JOptionPane.showMessageDialog(dlg, "Cannot remove 'All' category");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(dlg, "Remove category '" + name + "'? Foods in this category will be moved to 'All'.", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                currentUser.myCategories.remove(name);
                catModel.remove(idx);
                // Move foods to "All"
                for (FoodItem fi : foods) {
                    if (currentUser.restaurantName.equals(fi.restaurantOwner) && name.equals(fi.category)) {
                        fi.category = "All";
                    }
                }
                refreshItemsByRestaurant(currentUser.restaurantName);
            }
        });

        return main;
    }

    private JPanel createOrderManagementPanel(JDialog dlg) {
        JPanel main = new JPanel(new BorderLayout());

        DefaultListModel<String> ordersModel = new DefaultListModel<>();
        // filter orders that have foods from this restaurant
        orders.stream()
            .filter(o -> o.items.stream().anyMatch(item -> currentUser.restaurantName.equals(item.food.restaurantOwner)))
            .forEach(o -> ordersModel.addElement(formatOrderLine(o)));
        JList<String> ordersList = new JList<>(ordersModel);
        main.add(new JScrollPane(ordersList), BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton prepare = new JButton("Mark PREPARING");
        JButton ready = new JButton("Mark READY FOR PICKUP");
        JButton cancel = new JButton("Cancel Order");
        JButton viewDetails = new JButton("View Order Details");
        prepare.addActionListener(e -> {
            int i = ordersList.getSelectedIndex();
            if (i < 0) return;
            Order o = findOrderFromDisplay(ordersModel.get(i));
            if (o != null) {
                o.status = OrderStatus.PREPARING;
                log("Restaurant " + currentUser.username + " set PREPARING order " + o.id);
                ordersModel.set(i, formatOrderLine(o));
            }
        });
        ready.addActionListener(e -> {
            int i = ordersList.getSelectedIndex();
            if (i < 0) return;
            Order o = findOrderFromDisplay(ordersModel.get(i));
            if (o != null) {
                o.status = OrderStatus.READY_FOR_PICKUP;
                log("Restaurant " + currentUser.username + " set READY order " + o.id);
                ordersModel.set(i, formatOrderLine(o));
            }
        });
        cancel.addActionListener(e -> {
            int i = ordersList.getSelectedIndex();
            if (i < 0) return;
            Order o = findOrderFromDisplay(ordersModel.get(i));
            if (o == null) return;
            if (o.status != OrderStatus.PLACED) {
                JOptionPane.showMessageDialog(dlg, "Can only cancel orders that are placed after 1 minute.");
                return;
            }
            long diff = new Date().getTime() - o.created.getTime();
            if (diff > 60000) { // 1 minute
                JOptionPane.showMessageDialog(dlg, "Cannot cancel order after 1 minute.");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(dlg, "Cancel this order?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                o.status = OrderStatus.CANCELLED;
                log("Order " + o.id + " cancelled by restaurant " + currentUser.username);
                ordersModel.set(i, formatOrderLine(o));
                JOptionPane.showMessageDialog(dlg, "Order cancelled.");
            }
        });
        viewDetails.addActionListener(e -> {
            int i = ordersList.getSelectedIndex();
            if (i < 0) return;
            Order o = findOrderFromDisplay(ordersModel.get(i));
            if (o == null) return;

            StringBuilder sb = new StringBuilder();
            sb.append("Order ID: ").append(o.id).append("\n");
            sb.append("Status: ").append(o.status).append("\n");
            sb.append("Ordered at: ").append(o.created).append("\n");
            sb.append("Address: ").append(o.addressSnapshot).append("\n");
            sb.append("Phone: ").append(o.phoneSnapshot).append("\n\n");
            sb.append("Items Ordered:\n");
            for (OrderItem item : o.items) {
                double itemPrice = item.food.price + item.food.variationPrices.getOrDefault(item.variation, 0.0);
                String variationText = item.variation.isEmpty() ? "" : " (" + item.variation + ")";
                sb.append("- ").append(item.food.name).append(variationText).append(" x").append(item.qty).append("  VND ").append(formatPrice(itemPrice * item.qty)).append("\n");
            }
            sb.append("\nTotal: VND ").append(String.format("%.2f", o.total)).append("\n");

            if (o.note != null && !o.note.isEmpty()) {
                sb.append("\nCustomer Note: ").append(o.note).append("\n");
            }

            if (o.assignedShipper != null) {
                User shipper = users.get(o.assignedShipper);
                if (shipper != null) {
                    sb.append("\nShipper Information:\n");
                    sb.append("Name: ").append(shipper.shipperName != null ? shipper.shipperName : shipper.username).append("\n");
                    sb.append("Phone: ").append(shipper.phone != null ? shipper.phone : "N/A").append("\n");
                    sb.append("Rating: ").append(String.format("%.1f", shipper.shipperRatings.stream().mapToDouble(d -> d).average().orElse(0))).append(" (").append(shipper.shipperRatings.size()).append(" reviews)\n");

                    if (o.status == OrderStatus.DELIVERING) {
                        sb.append("\n🚚 Delivery Information:\n");
                        sb.append("Shipper is currently delivering your order.\n");
                        sb.append("Contact shipper: ").append(shipper.phone != null ? shipper.phone : "N/A").append("\n");
                        sb.append("Estimated delivery: Soon\n");
                    }
                }
            } else {
                sb.append("\nNo shipper assigned yet.\n");
            }

            JTextArea ta = new JTextArea(sb.toString());
            ta.setEditable(false);
            JScrollPane sp = new JScrollPane(ta);
            sp.setPreferredSize(new Dimension(500, 400));
            JOptionPane.showMessageDialog(dlg, sp, "Order Details", JOptionPane.PLAIN_MESSAGE);
        });
        bottom.add(prepare);
        bottom.add(ready);
        bottom.add(cancel);
        bottom.add(viewDetails);
        main.add(bottom, BorderLayout.SOUTH);
        return main;
    }

    // Owner: view orders, mark preparing / ready
    private void showOwnerPanel() {
        JDialog dlg = new JDialog(frame, "Owner - Orders", true);
        dlg.setSize(800, 500);
        dlg.setLocationRelativeTo(frame);
        JPanel main = new JPanel(new BorderLayout());

        DefaultListModel<String> ordersModel = new DefaultListModel<>();
        orders.forEach(o -> ordersModel.addElement(formatOrderLine(o)));
        JList<String> ordersList = new JList<>(ordersModel);
        main.add(new JScrollPane(ordersList), BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton prepare = new JButton("Mark PREPARING");
        JButton ready = new JButton("Mark READY FOR PICKUP");
        prepare.addActionListener(e -> {
            int i = ordersList.getSelectedIndex();
            if (i < 0)
                return;
            Order o = orders.get(i);
            o.status = OrderStatus.PREPARING;
            log("Owner set PREPARING order " + o.id);
            ordersModel.set(i, formatOrderLine(o));
        });
        ready.addActionListener(e -> {
            int i = ordersList.getSelectedIndex();
            if (i < 0)
                return;
            Order o = orders.get(i);
            o.status = OrderStatus.READY_FOR_PICKUP;
            log("Owner set READY order " + o.id);
            ordersModel.set(i, formatOrderLine(o));
        });
        bottom.add(prepare);
        bottom.add(ready);
        main.add(bottom, BorderLayout.SOUTH);

        dlg.getContentPane().add(main);
        dlg.setVisible(true);
    }

    // Shipper: accept and mark shipped/delivered
    private void showShipperPanel() {
        JPanel right = new JPanel(new GridLayout(0, 1, 6, 6));
        JButton accept = new JButton("Accept (Assign to me)");
        JButton view = new JButton("View Details");
        JButton myOrdersBtn = new JButton("My Orders");
        right.add(accept);
        right.add(view);
        right.add(myOrdersBtn);
        if (currentUser == null || currentUser.role != Role.SHIPPER) {
            JOptionPane.showMessageDialog(frame, "Only shippers can open this panel.");
            return;
        }
        JDialog dlg = new JDialog(frame, "Shipper - Orders", true);
        dlg.setSize(900, 500);
        dlg.setLocationRelativeTo(frame);
        JPanel main = new JPanel(new BorderLayout());
        JButton fileComplaintBtn = new JButton("File Complaint");
        right.add(fileComplaintBtn);
        fileComplaintBtn.addActionListener(e -> {
            String text = JOptionPane.showInputDialog(dlg, "Describe your complaint:");
            if (text != null && !text.isBlank()) {
                Complaint c = new Complaint(currentUser.username, currentUser.role, text);
                complaints.add(c);
                log("Complaint filed by " + currentUser.username);
                JOptionPane.showMessageDialog(dlg, "Complaint submitted.");
            }
        });
        DefaultListModel<String> model = new DefaultListModel<>();
        // show available orders (not assigned)
        orders.forEach(o -> {
            if (o.assignedShipper == null
                    && (o.status == OrderStatus.READY_FOR_PICKUP || o.status == OrderStatus.PLACED))
                model.addElement(formatOrderLine(o));
        });

        JList<String> list = new JList<>(model);
        main.add(new JScrollPane(list), BorderLayout.CENTER);

        accept.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0)
                return;
            Order o = findOrderFromDisplay(model.get(i));
            if (o == null)
                return;
            synchronized (o) {
                if (o.assignedShipper != null) {
                    JOptionPane.showMessageDialog(dlg, "This order was already accepted by " + o.assignedShipper);
                    return;
                }
                o.assignedShipper = currentUser.username;
                o.status = OrderStatus.ACCEPTED_BY_SHIPPER;
                log("Shipper " + currentUser.username + " accepted order " + o.id);
                model.remove(i);
                JOptionPane.showMessageDialog(dlg, "Order accepted. Open 'My Orders' to manage it.");
            }
        });

        view.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0)
                return;
            Order o = findOrderFromDisplay(model.get(i));
            if (o == null)
                return;
            JOptionPane.showMessageDialog(dlg, formatOrderDetailed(o));
        });

        myOrdersBtn.addActionListener(e -> {
            // show orders assigned to me
            DefaultListModel<String> myModel = new DefaultListModel<>();
            orders.forEach(o -> {
                if (currentUser.username.equals(o.assignedShipper))
                    myModel.addElement(formatOrderLine(o));
            });
            JList<String> myList = new JList<>(myModel);
            JButton inTransit = new JButton("Mark DELIVERING");
            JButton delivered = new JButton("Mark DELIVERED");
            JButton chatBtn = new JButton("Open Chat");
            JPanel bot = new JPanel();
            bot.add(inTransit);
            bot.add(delivered);
            bot.add(chatBtn);

            JDialog md = new JDialog(dlg, "My Orders - " + currentUser.username, true);
            md.setSize(800, 450);
            md.setLocationRelativeTo(dlg);
            md.getContentPane().add(new JScrollPane(myList), BorderLayout.CENTER);
            md.getContentPane().add(bot, BorderLayout.SOUTH);

            inTransit.addActionListener(ae -> {
                int sel = myList.getSelectedIndex();
                if (sel < 0)
                    return;
                Order o = findOrderFromDisplay(myModel.get(sel));
                if (o != null && currentUser.username.equals(o.assignedShipper)
                        && o.status == OrderStatus.ACCEPTED_BY_SHIPPER) {
                    o.status = OrderStatus.DELIVERING;
                    log("Shipper " + currentUser.username + " set DELIVERING for " + o.id);
                    myModel.set(sel, formatOrderLine(o));
                }
            });

            delivered.addActionListener(ae -> {
                int sel = myList.getSelectedIndex();
                if (sel < 0)
                    return;
                Order o = findOrderFromDisplay(myModel.get(sel));
                if (o != null && currentUser.username.equals(o.assignedShipper) && o.status == OrderStatus.DELIVERING) {
                    o.status = OrderStatus.DELIVERED;
                    // clear chat history when order is completed
                    o.chat.clear();
                    log("Shipper " + currentUser.username + " marked DELIVERED for " + o.id + " and cleared chat history");
                    myModel.set(sel, formatOrderLine(o));
                }
            });

            chatBtn.addActionListener(ae -> {
                int sel = myList.getSelectedIndex();
                if (sel < 0)
                    return;
                Order o = findOrderFromDisplay(myModel.get(sel));
                if (o != null && (o.status == OrderStatus.ACCEPTED_BY_SHIPPER || o.status == OrderStatus.DELIVERING)) {
                    showChatDialog(o);
                } else {
                    JOptionPane.showMessageDialog(md, "Chat available only when order is accepted by shipper or delivering.");
                }
            });

            md.setVisible(true);
        });

        main.add(right, BorderLayout.EAST);
        dlg.getContentPane().add(main);
        dlg.setVisible(true);
    }

    // Administrator: manage customer accounts & view logs, apply discounts
    private void showAdministratorPanel() {
        JDialog dlg = new JDialog(frame, "Administrator - Accounts & Logs", true);
        dlg.setSize(800, 600);
        dlg.setLocationRelativeTo(frame);
        JPanel main = new JPanel(new BorderLayout());
        DefaultListModel<String> userModel = new DefaultListModel<>();
        users.values().forEach(u -> userModel.addElement(u.username + " - " + u.role));
        JList<String> userList = new JList<>(userModel);
        main.add(new JScrollPane(userList), BorderLayout.WEST);

        DefaultListModel<String> logModel = new DefaultListModel<>();
        logs.forEach(logModel::addElement);
        JList<String> logList = new JList<>(logModel);
        main.add(new JScrollPane(logList), BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton disable = new JButton("Delete Selected Customer");
        disable.addActionListener(e -> {
            int i = userList.getSelectedIndex();
            if (i < 0)
                return;
            String s = userModel.get(i);
            String uname = s.split(" - ")[0];
            User u = users.get(uname);
            if (u != null && u.role == Role.CUSTOMER) {
                users.remove(uname);
                userModel.remove(i);
                log("Administrator deleted customer: " + uname);
                JOptionPane.showMessageDialog(dlg, "Deleted " + uname);
            } else {
                JOptionPane.showMessageDialog(dlg, "Can only delete customers.");
            }
        });
        JButton changeUserImg = new JButton("Change User Image");
        changeUserImg.addActionListener(ev -> {
            int i = userList.getSelectedIndex();
            if (i < 0)
                return;
            String s = userModel.get(i);
            String uname = s.split(" - ")[0];
            User u = users.get(uname);
            if (u == null)
                return;
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                u.profileImagePath = chooser.getSelectedFile().getAbsolutePath();
                log("Administrator changed profile image for " + u.username);
                JOptionPane.showMessageDialog(dlg, "Profile image updated for " + u.username);
            }
        });
        bottom.add(changeUserImg);
        JButton viewAllOrders = new JButton("View All Orders");
        viewAllOrders.addActionListener(e -> {
            String text = orders.stream().map(this::formatOrderDetailed).collect(Collectors.joining("\n\n"));
            JTextArea ta = new JTextArea(text);
            ta.setEditable(false);
            JScrollPane sp = new JScrollPane(ta);
            sp.setPreferredSize(new Dimension(600, 400));
            JOptionPane.showMessageDialog(dlg, sp, "Orders", JOptionPane.PLAIN_MESSAGE);
        });
        bottom.add(disable);
        bottom.add(viewAllOrders);
        main.add(bottom, BorderLayout.SOUTH);

        dlg.getContentPane().add(main);
        dlg.setVisible(true);
    }

    // Customer service: see complaints and resolve
    private void showCustomerServicePanel() {
        JDialog dlg = new JDialog(frame, "Customer Service - Complaints", true);
        dlg.setSize(800, 500);
        dlg.setLocationRelativeTo(frame);
        JPanel main = new JPanel(new BorderLayout());

        DefaultListModel<String> model = new DefaultListModel<>();
        // Add complaints
        for (Complaint c : complaints)
            model.addElement(c.toString());

        // Add orders with complaints
        for (Order o : orders) {
            if (o.complaint != null)
                model.addElement(formatOrderLine(o) + " COMPLAINT: " + o.complaint);
        }

        JList<String> list = new JList<>(model);
        main.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton resolve = new JButton("Resolve & Refund");
        resolve.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0)
                return;
            Order o = findOrderFromDisplay(model.get(i));
            if (o != null) {
                o.complaint = null;
                model.remove(i);
                log("Customer service resolved complaint for order " + o.id);
                JOptionPane.showMessageDialog(dlg, "Resolved. (In real system, would issue refund/credit)");
            }
        });
        JButton view = new JButton("View Order Details");
        view.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0)
                return;
            Order o = findOrderFromDisplay(model.get(i));
            if (o != null)
                JOptionPane.showMessageDialog(dlg, formatOrderDetailed(o));
        });
        bottom.add(view);
        bottom.add(resolve);
        main.add(bottom, BorderLayout.SOUTH);

        dlg.getContentPane().add(main);
        dlg.setVisible(true);
    }

    // Customer panel: my orders, complaint
    // CUSTOMER: View orders + profile settings
    private void showCustomerPanel() {
        // Show customer's own orders
        DefaultListModel<String> model = new DefaultListModel<>();
        orders.forEach(o -> {
            if (o.customer == currentUser)
                model.addElement(formatOrderLine(o));
        });
        JDialog dlg = new JDialog(frame, "Customer Dashboard", true);
        dlg.setSize(700, 600);
        dlg.setLocationRelativeTo(frame);
        dlg.getContentPane().setBackground(new Color(245, 245, 245));

        JPanel main = new JPanel(new BorderLayout());
        main.setBorder(new EmptyBorder(10, 10, 10, 10));
        main.setBackground(new Color(245, 245, 245));

        JList<String> list = new JList<>(model);
        main.add(new JScrollPane(list), BorderLayout.CENTER);

        JButton chatBtn = new JButton("Open Chat");
        chatBtn.setBackground(new Color(100, 149, 237));
        chatBtn.setForeground(Color.WHITE);
        chatBtn.setFocusPainted(false);
        chatBtn.setOpaque(true);
        chatBtn.setPreferredSize(new Dimension(80, 25));
        chatBtn.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0)
                return;
            Order o = findOrderFromDisplay(model.get(i));
            if (o != null && (o.status == OrderStatus.ACCEPTED_BY_SHIPPER || o.status == OrderStatus.DELIVERING)) {
                showChatDialog(o);
            } else {
                JOptionPane.showMessageDialog(dlg, "Chat available only when order is accepted by shipper or delivering.");
            }
        });
        JPanel bottom = new JPanel(new GridLayout(0, 4, 8, 8));
        bottom.setBackground(new Color(245, 245, 245));
        bottom.add(chatBtn);

        // NEW BUTTON: Profile settings
        JButton settingsBtn = new JButton("Profile Settings");
        settingsBtn.setBackground(new Color(147, 112, 219));
        settingsBtn.setForeground(Color.WHITE);
        settingsBtn.setFocusPainted(false);
        settingsBtn.setOpaque(true);
        settingsBtn.setPreferredSize(new Dimension(80, 25));
        settingsBtn.addActionListener(e -> showCustomerSettingsDialog());
        bottom.add(settingsBtn);

        JButton complainBtn = new JButton("File Complaint");
        complainBtn.setBackground(new Color(255, 165, 0));
        complainBtn.setForeground(Color.WHITE);
        complainBtn.setFocusPainted(false);
        complainBtn.setOpaque(true);
        complainBtn.setPreferredSize(new Dimension(80, 25));
        complainBtn.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0)
                return;
            Order o = findOrderFromDisplay(model.get(i));
            if (o == null)
                return;

            String text = JOptionPane.showInputDialog(dlg,
                    "Describe your issue for order " + o.id + ":");

            if (text != null && !text.isBlank()) {
                o.complaint = text;
                log("Complaint filed by " + currentUser.username + " on order " + o.id);
                JOptionPane.showMessageDialog(dlg, "Complaint submitted.");
            }
        });
        bottom.add(complainBtn);

        JButton rateBtn = new JButton("Rate Delivered Order");
        rateBtn.setBackground(new Color(60, 179, 113));
        rateBtn.setForeground(Color.WHITE);
        rateBtn.setFocusPainted(false);
        rateBtn.setOpaque(true);
        rateBtn.setPreferredSize(new Dimension(80, 25));
        rateBtn.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0)
                return;
            Order o = findOrderFromDisplay(model.get(i));
            if (o == null || o.status != OrderStatus.DELIVERED)
                return;
            showRateOrderDialog(o);
        });
        bottom.add(rateBtn);

        JButton cancelBtn = new JButton("Cancel Order");
        cancelBtn.setBackground(new Color(220, 20, 60));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setOpaque(true);
        cancelBtn.setPreferredSize(new Dimension(80, 25));
        cancelBtn.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0) return;
            Order o = findOrderFromDisplay(model.get(i));
            if (o == null) return;
            if (o.customer != currentUser) {
                JOptionPane.showMessageDialog(dlg, "You can only cancel your own orders.");
                return;
            }
            if (o.status != OrderStatus.PLACED) {
                JOptionPane.showMessageDialog(dlg, "Can only cancel orders that are placed.");
                return;
            }
            long diff = new Date().getTime() - o.created.getTime();
            if (diff > 60000) { // 1 minute = 60000 ms
                JOptionPane.showMessageDialog(dlg, "Cannot cancel order after 1 minute.");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(dlg, "Cancel this order?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                o.status = OrderStatus.CANCELLED;
                log("Order " + o.id + " cancelled by customer " + currentUser.username);
                model.set(i, formatOrderLine(o));
                JOptionPane.showMessageDialog(dlg, "Order cancelled.");
            }
        });
        bottom.add(cancelBtn);

        JButton orderHistoryBtn = new JButton("View more order");
        orderHistoryBtn.setBackground(new Color(32, 178, 170));
        orderHistoryBtn.setForeground(Color.WHITE);
        orderHistoryBtn.setFocusPainted(false);
        orderHistoryBtn.setPreferredSize(new Dimension(80, 25));
        orderHistoryBtn.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0) return;
            Order o = findOrderFromDisplay(model.get(i));
            if (o == null) return;

            StringBuilder sb = new StringBuilder();
            sb.append("Order ID: ").append(o.id).append("\n");
            sb.append("Status: ").append(o.status).append("\n");
            sb.append("Ordered at: ").append(o.created).append("\n");
            sb.append("Address: ").append(o.addressSnapshot).append("\n");
            sb.append("Phone: ").append(o.phoneSnapshot).append("\n\n");
            sb.append("Items Ordered:\n");
            for (OrderItem item : o.items) {
                double itemPrice = item.food.price + item.food.variationPrices.getOrDefault(item.variation, 0.0);
                String variationText = item.variation.isEmpty() ? "" : " (" + item.variation + ")";
                sb.append("- ").append(item.food.name).append(variationText).append(" x").append(item.qty).append("  VND ").append(formatPrice(itemPrice * item.qty)).append("\n");
            }
            sb.append("\nTotal: VND ").append(String.format("%.2f", o.total)).append("\n");

            if (o.note != null && !o.note.isEmpty()) {
                sb.append("\nCustomer Note: ").append(o.note).append("\n");
            }

            if (o.assignedShipper != null) {
                User shipper = users.get(o.assignedShipper);
                if (shipper != null) {
                    sb.append("\nShipper Information:\n");
                    sb.append("Name: ").append(shipper.shipperName != null ? shipper.shipperName : shipper.username).append("\n");
                    sb.append("Phone: ").append(shipper.phone != null ? shipper.phone : "N/A").append("\n");
                    sb.append("Rating: ").append(String.format("%.1f", shipper.shipperRatings.stream().mapToDouble(d -> d).average().orElse(0))).append(" (").append(shipper.shipperRatings.size()).append(" reviews)\n");

                    if (o.status == OrderStatus.DELIVERING) {
                        sb.append("\n🚚 Delivery Information:\n");
                        sb.append("Shipper is currently delivering your order.\n");
                        sb.append("Contact shipper: ").append(shipper.phone != null ? shipper.phone : "N/A").append("\n");
                        sb.append("Estimated delivery: Soon\n");
                    }
                }
            } else {
                sb.append("\nNo shipper assigned yet.\n");
            }

            JTextArea ta = new JTextArea(sb.toString());
            ta.setEditable(false);
            JScrollPane sp = new JScrollPane(ta);
            sp.setPreferredSize(new Dimension(500, 400));
            JOptionPane.showMessageDialog(dlg, sp, "Order Details", JOptionPane.PLAIN_MESSAGE);
        });
        bottom.add(orderHistoryBtn);

        JButton trackShipperBtn = new JButton("Track Shipper");
        trackShipperBtn.setBackground(new Color(0, 191, 255));
        trackShipperBtn.setForeground(Color.WHITE);
        trackShipperBtn.setFocusPainted(false);
        trackShipperBtn.setPreferredSize(new Dimension(80, 25));
        trackShipperBtn.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0) return;
            Order o = findOrderFromDisplay(model.get(i));
            if (o == null) return;
            if (o.status != OrderStatus.DELIVERING || o.assignedShipper == null) {
                JOptionPane.showMessageDialog(dlg, "Tracking available only when order is being delivered.");
                return;
            }
            showVirtualMapDialog(o);
        });
        bottom.add(trackShipperBtn);

        JButton reorderBtn = new JButton("Reorder");
        reorderBtn.setBackground(new Color(124, 252, 0));
        reorderBtn.setForeground(Color.BLACK);
        reorderBtn.setFocusPainted(false);
        reorderBtn.setPreferredSize(new Dimension(80, 25));
        reorderBtn.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0) return;
            Order o = findOrderFromDisplay(model.get(i));
            if (o == null) return;

            // Add all items from the order to cart
            for (OrderItem item : o.items) {
                cart.computeIfAbsent(item.food, k -> new HashMap<>()).merge(item.variation, item.qty, Integer::sum);
            }
            updateCartButton();
            JOptionPane.showMessageDialog(dlg, "Items added to cart! You can now checkout.");
        });
        bottom.add(reorderBtn);

        main.add(bottom, BorderLayout.SOUTH);

        dlg.getContentPane().add(main);
        dlg.setVisible(true);
    }

    private void showFoodReviewsDialog(FoodItem f) {
        JDialog dlg = new JDialog(frame, "Reviews for " + f.name, true);
        dlg.setSize(400, 300);
        dlg.setLocationRelativeTo(frame);

        DefaultListModel<String> model = new DefaultListModel<>();
        for (int i = 0; i < f.ratings.size(); i++) {
            String comment = i < f.comments.size() ? f.comments.get(i) : "";
            model.addElement("⭐ " + f.ratings.get(i) + " - " + comment);
        }

        JList<String> list = new JList<>(model);
        dlg.getContentPane().add(new JScrollPane(list));
        dlg.setVisible(true);
    }

    private void showRateOrderDialog(FoodDeliveryApp.Order o) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Food ratings
        p.add(new JLabel("Rate the foods:"));
        Map<FoodItem, JComboBox<Integer>> foodRatingBoxes = new HashMap<>();
        Map<FoodItem, JTextArea> foodCommentAreas = new HashMap<>();
        for (OrderItem item : o.items) {
            JPanel foodPanel = new JPanel(new BorderLayout());
            foodPanel.setBorder(BorderFactory.createTitledBorder(item.food.name));
            JComboBox<Integer> ratingBox = new JComboBox<>(new Integer[]{0,1,2,3,4,5});
            ratingBox.setSelectedItem(5); // default
            foodRatingBoxes.put(item.food, ratingBox);
            JTextArea commentArea = new JTextArea(2, 20);
            foodCommentAreas.put(item.food, commentArea);
            foodPanel.add(new JLabel("Rating:"), BorderLayout.WEST);
            foodPanel.add(ratingBox, BorderLayout.CENTER);
            foodPanel.add(new JScrollPane(commentArea), BorderLayout.SOUTH);
            p.add(foodPanel);
        }

        // Shipper rating
        JPanel shipperPanel = new JPanel(new BorderLayout());
        shipperPanel.setBorder(BorderFactory.createTitledBorder("Rate the Shipper"));
        JComboBox<Integer> shipperRatingBox = new JComboBox<>(new Integer[]{0,1,2,3,4,5});
        shipperRatingBox.setSelectedItem(5);
        JTextArea shipperCommentArea = new JTextArea(2, 20);
        shipperPanel.add(new JLabel("Rating:"), BorderLayout.WEST);
        shipperPanel.add(shipperRatingBox, BorderLayout.CENTER);
        shipperPanel.add(new JScrollPane(shipperCommentArea), BorderLayout.SOUTH);
        p.add(shipperPanel);

        JScrollPane scroll = new JScrollPane(p);
        scroll.setPreferredSize(new Dimension(500, 400));

        int res = JOptionPane.showConfirmDialog(frame, scroll, "Rate Order", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            // Save ratings and comments
            for (OrderItem item : o.items) {
                FoodItem f = item.food;
                int rating = (Integer) foodRatingBoxes.get(f).getSelectedItem();
                String comment = foodCommentAreas.get(f).getText().trim();
                o.foodRatings.put(f, (double) rating);
                if (!comment.isEmpty()) {
                    o.foodComments.put(f, comment);
                    f.comments.add(comment);
                }
                f.ratings.add((double) rating);
                f.updateRating();
            }
            int shipperRating = (Integer) shipperRatingBox.getSelectedItem();
            String shipperComment = shipperCommentArea.getText().trim();
            o.shipperRating = (double) shipperRating;
            if (!shipperComment.isEmpty()) {
                o.shipperComment = shipperComment;
                User shipper = users.get(o.assignedShipper);
                if (shipper != null) {
                    shipper.shipperRatings.add((double) shipperRating);
                    shipper.shipperComments.add(shipperComment);
                }
            }
            log("Customer " + currentUser.username + " rated order " + o.id);
            JOptionPane.showMessageDialog(frame, "Thank you for your feedback!");
        }
    }
    private void showChatDialog(Order o) {
        JDialog dlg = new JDialog(frame, "Chat - Order " + o.id.toString().substring(0, 6), true);
        dlg.setSize(600, 400);
        dlg.setLocationRelativeTo(frame);
        JPanel main = new JPanel(new BorderLayout());

        DefaultListModel<String> model = new DefaultListModel<>();
        for (Message m : o.chat) {
            model.addElement(String.format("[%tR] %s: %s", m.time, m.sender, m.text));
        }
        JList<String> list = new JList<>(model);
        main.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        JTextField input = new JTextField();
        JButton send = new JButton("Send");

        send.addActionListener(e -> {
            String txt = input.getText().trim();
            if (txt.isEmpty())
                return;
            Message m = new Message(currentUser.username, txt);
            o.chat.add(m);
            model.addElement(String.format("[%tR] %s: %s", m.time, m.sender, m.text));
            input.setText("");
        });

        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        main.add(bottom, BorderLayout.SOUTH);

        dlg.getContentPane().add(main);
        dlg.setVisible(true);
    }

    private void log(String line) {
        String entry = new Date() + " - " + line;
        logs.add(entry);
        System.out.println(entry); // also print to console
    }

    private void refreshForRole() {
        // enabling/disabling admin inline controls is easier to handle by re-creating
        // UI
        refreshItems(catList.getSelectedValue());
    }

    private Color randomPastelColor() {
        Random r = new Random();
        int red = 150 + r.nextInt(106);
        int green = 150 + r.nextInt(106);
        int blue = 150 + r.nextInt(106);
        return new Color(red, green, blue);
    }

    private String formatOrderLine(Order o) {
        return String.format("[%s] %s - VND %s - %s", o.id.toString().substring(0, 6), o.customer.username, formatPrice(o.total),
                o.status);
    }

    private String formatOrderDetailed(Order o) {
        StringBuilder sb = new StringBuilder();
        sb.append("Order ID: ").append(o.id).append("\n");
        sb.append("Customer: ").append(o.customer.username).append("\n");
        sb.append("Address: ").append(o.addressSnapshot).append("\n");
        sb.append("Phone: ").append(o.phoneSnapshot).append("\n");
        sb.append("Created: ").append(o.created).append("\n");
        sb.append("Status: ").append(o.status).append("\n");
        sb.append("Items:\n");
        for (OrderItem it : o.items) {
            double itemPrice = it.food.price + it.food.variationPrices.getOrDefault(it.variation, 0.0);
            sb.append("  - ").append(it.food.name).append(it.variation.isEmpty() ? "" : " (" + it.variation + ")").append(" x").append(it.qty).append("  VND ")
                    .append(formatPrice(itemPrice * it.qty)).append("\n");
        }
        sb.append("Total: VND ").append(formatPrice(o.total)).append("\n");
        if (o.complaint != null)
            sb.append("Complaint: ").append(o.complaint).append("\n");
        if (o.assignedShipper != null) {
            User shipper = users.get(o.assignedShipper);
            String shipperDisplay = shipper != null && shipper.shipperName != null ? shipper.shipperName : o.assignedShipper;
            sb.append("Shipper: ").append(shipperDisplay).append("\n");
        }
        return sb.toString();
    }

    // Find an order by the display line (naive)
    private Order findOrderFromDisplay(String display) {
        if (display == null)
            return null;
        for (Order o : orders) {
            if (display.contains(o.id.toString().substring(0, 6)))
                return o;
        }
        return null;
    }

    private void loadCategories() {
        categories.clear(); // Quan trọng: xóa toàn bộ trước
        categories.add("All"); // Thêm đúng một lần
        for (FoodItem f : foods) {
            if (f.category != null && !categories.contains(f.category)) {
                categories.add(f.category);
            }
        }
        catList.setListData(categories.toArray(new String[0])); // cập nhật UI
        if (categories.size() > 0) {
            catList.setSelectedIndex(0);
        }
    }

    private void loadCategoriesForRestaurant(String restaurantName) {
        if (restaurantName == null) {
            return; // Don't load if restaurant name is null
        }
        categories.clear();
        categories.add("All");
        for (FoodItem f : foods) {
            if (restaurantName.equals(f.restaurantOwner)) {
                if (f.category != null && !f.category.isBlank() && !categories.contains(f.category)) {
                    categories.add(f.category);
                }
            }
        }
        catList.setListData(categories.toArray(new String[0]));
        if (categories.size() > 0) {
            catList.setSelectedIndex(0);
        }
    }

    // Custom renderer for restaurant list with status colors
    private class RestaurantListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(true);
            if (isSelected) {
                panel.setBackground(list.getSelectionBackground());
            } else {
                panel.setBackground(list.getBackground());
            }

            String text = (String) value;
            String restaurantName = text.replace(" (Open)", "").replace(" (Closed)", "");
            String status = text.contains(" (Open)") ? " (Open)" : text.contains(" (Closed)") ? " (Closed)" : "";

            // Find the restaurant user
            User restUser = users.values().stream()
                .filter(u -> u.role == Role.RESTAURANT && restaurantName.equals(u.restaurantName))
                .findFirst().orElse(null);

            // Icon - now smaller for better performance
            ImageIcon icon = null;
            if (restUser != null && restUser.profileImagePath != null) {
                icon = loadScaledImageIcon(restUser.profileImagePath, 120, 120);
            }
            if (icon == null) {
                // Default icon or no icon
                icon = new ImageIcon(); // empty
            }
            JLabel iconLabel = new JLabel(icon);
            iconLabel.setPreferredSize(new Dimension(120, 120));
            iconLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center the image
            panel.add(iconLabel, BorderLayout.NORTH);

            // Text - now centered below the image
            JLabel textLabel = new JLabel("<html><center>" + restaurantName + "<br>" + status + "</center></html>");
            textLabel.setFont(textLabel.getFont().deriveFont(14f)); // Bigger font
            textLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center the text
            if (isSelected) {
                textLabel.setForeground(Color.BLACK); // Dark color when selected
            } else {
                // Original colors when not selected
                if (status.contains("Open")) {
                    textLabel.setForeground(Color.GREEN);
                } else if (status.contains("Closed")) {
                    textLabel.setForeground(Color.RED);
                } else {
                    textLabel.setForeground(Color.BLACK); // Default color
                }
            }
            panel.add(textLabel, BorderLayout.CENTER);

            return panel;
        }
    }

    private class FoodListRenderer extends DefaultListCellRenderer {
        private List<FoodItem> foods;

        public FoodListRenderer(List<FoodItem> foods) {
            this.foods = foods;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(true);
            if (isSelected) {
                panel.setBackground(list.getSelectionBackground());
            } else {
                panel.setBackground(list.getBackground());
            }

            if (index >= 0 && index < foods.size()) {
                FoodItem f = foods.get(index);
                // Icon - bigger square image
                ImageIcon icon = loadScaledImageIcon(f.imagePath, 100, 100);
                if (icon == null) {
                    icon = new ImageIcon(); // empty
                }
                JLabel iconLabel = new JLabel(icon);
                iconLabel.setPreferredSize(new Dimension(100, 100));
                panel.add(iconLabel, BorderLayout.WEST);

                // Text
                String text = f.name + " (VND " + f.price + ")";
                if (f.description != null && !f.description.isEmpty()) {
                    text += " - " + f.description;
                }
                JLabel textLabel = new JLabel(text);
                panel.add(textLabel, BorderLayout.CENTER);
            }

            return panel;
        }
    }

    // -------------------- Main --------------------
    public static void main(String[] args) {
        try {
            new FoodDeliveryApp();
        } catch (Throwable t) {
            t.printStackTrace();
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("error.log", true))) {
                pw.println(new java.util.Date());
                t.printStackTrace(pw);
                pw.println();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            javax.swing.SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Application failed to start. See error.log in project folder.\n" + t,
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    // -------------------- WrapLayout (small helper for responsive wrapping)
    // --------------------
    // Source: public domain simplified wrap layout for Swing (keeps cards wrapped)
    static class WrapLayout extends FlowLayout {
        public WrapLayout() {
            super();
        }

        public WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension d = layoutSize(target, false);
            d.width -= (getHgap() + 1);
            return d;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getWidth();
                if (targetWidth == 0)
                    targetWidth = Integer.MAX_VALUE;
                Insets insets = target.getInsets();
                int hgap = getHgap();
                int vgap = getVgap();
                int maxWidth = targetWidth - (insets.left + insets.right + hgap * 2);

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0, rowHeight = 0;
                int nmembers = target.getComponentCount();

                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (!m.isVisible())
                        continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowWidth + d.width > maxWidth) {
                        dim.width = Math.max(dim.width, rowWidth);
                        dim.height += rowHeight + vgap;
                        rowWidth = 0;
                        rowHeight = 0;
                    }
                    rowWidth += d.width + hgap;
                    rowHeight = Math.max(rowHeight, d.height);
                }
                dim.width = Math.max(dim.width, rowWidth);
                dim.height += rowHeight;
                dim.width += insets.left + insets.right + hgap * 2;
                dim.height += insets.top + insets.bottom + vgap * 2;
                return dim;
            }
        }
    }
}
