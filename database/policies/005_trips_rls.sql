-- RLS Policies for trips table
ALTER TABLE trips ENABLE ROW LEVEL SECURITY;

-- 1. Users can view their own trips
CREATE POLICY "Users can view own trips" ON trips
    FOR SELECT USING (auth.uid() = user_id);

-- 2. Users can create their own trips
CREATE POLICY "Users can insert own trips" ON trips
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- 3. Users can update their own trips
CREATE POLICY "Users can update own trips" ON trips
    FOR UPDATE USING (auth.uid() = user_id);

-- 4. Users can delete their own trips (soft archive preferred)
CREATE POLICY "Users can delete own trips" ON trips
    FOR DELETE USING (auth.uid() = user_id);
