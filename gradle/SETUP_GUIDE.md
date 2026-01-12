Here's the exact same content translated to English with the same formatting:


# Complete Setup Guide for Gloom

This comprehensive guide will help you fully set up the project on your own Supabase project.

## Prerequisites

- Account on [Supabase](https://supabase.com)
- Android Studio Arctic Fox or newer
- Android device or emulator with API 26+
- Internet connection

## Step 1: Creating Supabase Project

1. **Registration/Login:**
   - Go to [supabase.com](https://supabase.com)
   - Login or register

2. **Creating Project:**
   - Click **"New Project"**
   - Fill in the data:
     - **Organization**: select or create an organization
     - **Name**: `miniclip-demo` (recommended)
     - **Database Password**: save a strong password
     - **Region**: `West Europe` (or closest to you)
   - Click **"Create new project"**
   - Wait for completion (1-3 minutes)

## Step 2: Getting API Keys

After creating the project:

1. Go to **Settings → API** in the left menu
2. Copy the following values: Project URL, Project API keys

**Save these values** - they will be needed later.

## Step 3: Database Setup

### 3.1 Creating Users Table (`users`)

Go to **SQL Editor** and execute the query:

```sql
-- Users table
CREATE TABLE users (
    id UUID REFERENCES auth.users PRIMARY KEY,
    email TEXT,
    username TEXT,
    profilePic TEXT DEFAULT '',
    followerList TEXT[] DEFAULT '{}',
    followingList TEXT[] DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- Enable Row Level Security
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

-- Security policies for users
CREATE POLICY "Users can view all profiles" ON users FOR SELECT USING (true);
CREATE POLICY "Users can update own profile" ON users FOR UPDATE USING (auth.uid() = id);
CREATE POLICY "Users can insert own profile" ON users FOR INSERT WITH CHECK (auth.uid() = id);
```

### 3.2 Creating Videos Table (`videos`)

Execute the following query:

```sql
-- Videos table
CREATE TABLE videos (
    video_id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    title TEXT,
    url TEXT,
    uploader_id UUID REFERENCES auth.users,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- Enable RLS
ALTER TABLE videos ENABLE ROW LEVEL SECURITY;

-- Security policies for videos
CREATE POLICY "Anyone can view videos" ON videos FOR SELECT USING (true);
CREATE POLICY "Authenticated users can insert videos" ON videos FOR INSERT WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "Users can update own videos" ON videos FOR UPDATE USING (auth.uid() = uploader_id);
CREATE POLICY "Users can delete own videos" ON videos FOR DELETE USING (auth.uid() = uploader_id);
```

## Step 4: Storage Setup (File Storage)

### 4.1 Creating Buckets

Go to **Storage → Policies**:

**Bucket `profile-pictures`:**
- Click **"New Bucket"**
- **Name**: `profile-pictures`
- **Public**: ✅ enabled
- Click **"Create bucket"**

**Bucket `videos`:**
- Click **"New Bucket"**
- **Name**: `videos`
- **Public**: ✅ enabled
- Click **"Create bucket"**

### 4.2 Storage Policies Setup

For each bucket, create policies in **SQL Editor**:

**For `profile-pictures`:**
```sql
-- View profile pictures for everyone
CREATE POLICY "Anyone can view profile pictures" ON storage.objects 
FOR SELECT USING (bucket_id = 'profile-pictures');

-- Upload only own profile pictures
CREATE POLICY "Users can upload own profile pictures" ON storage.objects 
FOR INSERT WITH CHECK (
    bucket_id = 'profile-pictures' AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Update only own profile pictures
CREATE POLICY "Users can update own profile pictures" ON storage.objects 
FOR UPDATE USING (
    bucket_id = 'profile-pictures' AND auth.uid()::text = (storage.foldername(name))[1]
);
```

**For `videos`:**
```sql
-- View videos for everyone
CREATE POLICY "Anyone can view videos" ON storage.objects 
FOR SELECT USING (bucket_id = 'videos');

-- Upload videos for authorized users
CREATE POLICY "Authenticated users can upload videos" ON storage.objects 
FOR INSERT WITH CHECK (
    bucket_id = 'videos' AND auth.role() = 'authenticated'
);

-- Delete only own videos
CREATE POLICY "Users can delete own videos" ON storage.objects 
FOR DELETE USING (
    bucket_id = 'videos' AND auth.uid()::text = (storage.foldername(name))[1]
);
```

## Step 5: Authentication Setup

1. Go to **Authentication → Settings**
2. In **"URL Configuration"** section:
    - **Site URL**: your Project URL (e.g., `https://xxxxxxxxxx.supabase.co`)
    - **Redirect URLs**: add `com.desolate.gloom://login-callback`

3. In **"Auth Providers"** section:
    - ✅ **Email**: enable email provider
    - **Confirm email**: can be disabled for testing
    - Click **"Save"**

## Step 6: Android App Setup

### 6.1 Cloning the Project
```bash
git clone https://github.com/your-username/repository-name.git
cd repository-name
```

### 6.2 Replacing API Keys

Open file: `app/src/main/java/com/desolate/gloom/SupabaseManager.kt`

Replace the values:

```kotlin
object SupabaseManager {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = "https://YOUR-PROJECT.supabase.co", // Your Project URL
        supabaseKey = "YOUR_ANON_PUBLIC_KEY" // Your anon public key
    ) {
        install(Auth) {
            alwaysAutoRefresh = true
            autoSaveToStorage = true
        }
        install(Postgrest)
        install(Storage)
    }
}
```

### 6.3 Building the Project

1. Open the project in Android Studio
2. Wait for indexing and dependency loading to complete
3. Execute: **File → Sync Project with Gradle Files**
4. If there are errors - clean the project: **Build → Clean Project**

## Step 7: Launching the App

### 7.1 On Emulator:
- **Tools → Device Manager**
- Create an emulator with API 26+
- **Run → Run 'app'**

### 7.2 On Physical Device:
- Enable **Developer options** and **USB debugging**
- Connect the device
- **Run → Run 'app'**

## Step 8: Testing Functionality

Test the main functions:

**Registration/Login:**
- Create a new account
- Login to existing account

**Profile:**
- Upload an avatar
- Check profile editing

**Videos:**
- Upload a test video
- Browse the feed
- Check full-screen viewing

**Social Functions:**
- Subscriptions/unsubscriptions
- Viewing other profiles

