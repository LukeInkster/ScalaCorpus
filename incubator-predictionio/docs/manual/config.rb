require 'lib/custom_renderer'

# General Settings
set :css_dir,       'stylesheets'
set :js_dir,        'javascripts'
set :images_dir,    'images'
set :partials_dir,  'partials'

activate :directory_indexes
activate :gzip
activate :syntax, line_numbers: true
activate :autoprefixer

# Markdown
set :markdown_engine, :redcarpet
set :markdown,
    renderer: ::CustomRenderer,
    fenced_code_blocks: true,
    no_intra_emphasis: true,
    autolink: true,
    strikethrough: true,
    superscript: true,
    highlight: true,
    underline: true,
    tables: true

# Sprockets
sprockets.append_path File.join root, 'bower_components'

# Sitemap
set :url_root, 'https://docs.prediction.io'
activate :search_engine_sitemap, exclude_attr: 'hidden'

# Development Settings
configure :development do
  set :scheme, 'http'
  set :host, Middleman::PreviewServer.host rescue 'localhost'
  set :port, Middleman::PreviewServer.port rescue 80
  Slim::Engine.set_options pretty: false, sort_attrs: false
  set :debug_assets, true
end

# Build Settings
configure :build do
  set :scheme, 'https'
  set :host, 'docs.prediction.io'
  set :port, 80
  Slim::Engine.set_options pretty: false, sort_attrs: false
  activate :asset_hash
  activate :minify_css
  activate :minify_javascript
  activate :minify_html do |html|
    html.remove_multi_spaces        = true
    html.remove_comments            = true
    html.remove_intertag_spaces     = false
    html.remove_quotes              = false
    html.simple_doctype             = false
    html.remove_script_attributes   = true
    html.remove_style_attributes    = false
    html.remove_link_attributes     = false
    html.remove_form_attributes     = false
    html.remove_input_attributes    = false
    html.remove_javascript_protocol = true
    html.remove_http_protocol       = false
    html.remove_https_protocol      = false
    html.preserve_line_breaks       = false
    html.simple_boolean_attributes  = false
  end
end

# S3 Sync
activate :s3_sync do |s3_sync|
  s3_sync.bucket = 'docs.prediction.io'
  s3_sync.prefer_gzip = false
end

# Hacks

# https://github.com/middleman/middleman/issues/612
Slim::Engine.disable_option_validator!

# https://github.com/Aupajo/middleman-search_engine_sitemap/issues/2
set :file_watcher_ignore, [
  /^bin(\/|$)/,
  /^\.bundle(\/|$)/,
  # /^vendor(\/|$)/, # Keep this commented out!
  /^node_modules(\/|$)/,
  /^\.sass-cache(\/|$)/,
  /^\.cache(\/|$)/,
  /^\.git(\/|$)/,
  /^\.gitignore$/,
  /\.DS_Store/,
  /^\.rbenv-.*$/,
  /^Gemfile$/,
  /^Gemfile\.lock$/,
  /~$/,
  /(^|\/)\.?#/,
  /^tmp\//
]
