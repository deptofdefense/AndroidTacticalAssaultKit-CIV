package com.atakmap.util;

import java.util.*;

public class Profiler {

    public Measurement root = new Measurement("");
    Stack<Measurement> profile = new Stack<>();

    public Profiler() {
        profile.push(root);
    }

    public void push(String name) {
        Measurement m = profile.peek().children.get(name);
        if(m == null)
            profile.peek().children.put(name, m = new Measurement(name));
        profile.push(m);
        m.metrics.start();
    }

    public void pop() {
        profile.peek().metrics.stop();
        profile.pop();
    }

    public void reset() {
        reset(root);
    }

    public Measurement getProfile() {
        return root;
    }

    static void reset(Measurement m) {
        m.metrics.reset();
        for(Measurement c : m.children.values())
            reset(c);
    }

    public static class Measurement {
        public final String name;
        public Diagnostic metrics = new Diagnostic();
        public Map<String, Measurement> children = new TreeMap<>();

        Measurement(String name) {
            this.name = name;
        }
    }
}
